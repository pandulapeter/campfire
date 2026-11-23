/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

// The Linux web view helper (NativeWebView.kt): a WebKitGTK view in a GtkOffscreenWindow, with an ephemeral web
// context, so no cookie, cache, or storage outlives it.
//
// WebKitGTK is reached through dlopen (webkit2gtk-4.1, else 4.0), with the few functions it needs declared here, so the
// library builds with a bare C compiler and loads on a system without WebKitGTK, which then only cannot read pages.
//
// GTK is used from one thread only, the one that initialised it: the helper starts a thread of its own on first use
// and keeps it for the life of the process. AWT's GTK file dialog runs its own loop over the same default main context
// on another thread, so this thread iterates the context only while a view is open and otherwise sleeps, leaving the
// context free; every call from the JVM is queued for it and returns at once, and every answer comes back from it
// through the view's NativeWebViewEvents.

#include <dlfcn.h>
#include <jni.h>
#include <pthread.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>

typedef struct {
    uint32_t domain;
    int code;
    char *message;
} GError;

typedef void (*GCallback)(void);

enum { LOAD_STARTED = 0, LOAD_FINISHED = 3 };
enum { NAVIGATION_ACTION = 0, NEW_WINDOW_ACTION = 1, RESPONSE_ACTION = 2 };

static struct {
    int (*gtk_init_check)(int *, char ***);
    void *(*gtk_offscreen_window_new)(void);
    void (*gtk_container_add)(void *, void *);
    void (*gtk_widget_set_size_request)(void *, int, int);
    void (*gtk_widget_show_all)(void *);
    void (*gtk_widget_destroy)(void *);
    unsigned long (*g_signal_connect_data)(void *, const char *, GCallback, void *, void *, int);
    int (*g_main_context_iteration)(void *, int);
    void (*g_main_context_wakeup)(void *);
    const char *(*g_quark_to_string)(uint32_t);
    void (*g_free)(void *);
    void (*g_error_free)(GError *);
    void (*g_object_unref)(void *);
    void *(*webkit_web_context_new_ephemeral)(void);
    void *(*webkit_web_view_new_with_context)(void *);
    void *(*webkit_web_view_get_settings)(void *);
    void (*webkit_settings_set_auto_load_images)(void *, int);
    void (*webkit_settings_set_enable_javascript)(void *, int);
    void (*webkit_web_view_load_uri)(void *, const char *);
    void (*webkit_web_view_stop_loading)(void *);
    void (*webkit_web_view_run_javascript)(void *, const char *, void *, void (*)(void *, void *, void *), void *);
    void *(*webkit_web_view_run_javascript_finish)(void *, void *, GError **);
    void *(*webkit_javascript_result_get_js_value)(void *);
    void (*webkit_javascript_result_unref)(void *);
    int (*jsc_value_is_string)(void *);
    char *(*jsc_value_to_string)(void *);
    void *(*webkit_navigation_policy_decision_get_navigation_action)(void *);
    void *(*webkit_navigation_action_get_request)(void *);
    const char *(*webkit_uri_request_get_uri)(void *);
    void (*webkit_policy_decision_ignore)(void *);
    void (*webkit_policy_decision_use)(void *);
    int (*webkit_response_policy_decision_is_mime_type_supported)(void *);
    void *(*webkit_response_policy_decision_get_response)(void *);
    void *(*webkit_uri_response_get_http_headers)(void *);
    const char *(*soup_message_headers_get_one)(void *, const char *);
    void (*webkit_download_cancel)(void *);
} gtk;

static const char *const SYMBOLS[] = {
    "gtk_init_check", "gtk_offscreen_window_new", "gtk_container_add", "gtk_widget_set_size_request",
    "gtk_widget_show_all", "gtk_widget_destroy", "g_signal_connect_data", "g_main_context_iteration",
    "g_main_context_wakeup", "g_quark_to_string", "g_free", "g_error_free", "g_object_unref",
    "webkit_web_context_new_ephemeral", "webkit_web_view_new_with_context", "webkit_web_view_get_settings",
    "webkit_settings_set_auto_load_images", "webkit_settings_set_enable_javascript", "webkit_web_view_load_uri",
    "webkit_web_view_stop_loading", "webkit_web_view_run_javascript", "webkit_web_view_run_javascript_finish",
    "webkit_javascript_result_get_js_value", "webkit_javascript_result_unref", "jsc_value_is_string",
    "jsc_value_to_string", "webkit_navigation_policy_decision_get_navigation_action",
    "webkit_navigation_action_get_request", "webkit_uri_request_get_uri", "webkit_policy_decision_ignore",
    "webkit_policy_decision_use", "webkit_response_policy_decision_is_mime_type_supported",
    "webkit_response_policy_decision_get_response", "webkit_uri_response_get_http_headers",
    "soup_message_headers_get_one", "webkit_download_cancel",
};

// loadWebKit fills the table above slot by slot in this order.
_Static_assert(sizeof(gtk) == sizeof(SYMBOLS) / sizeof(SYMBOLS[0]) * sizeof(void *), "Every function has its name");

static JavaVM *javaVm;
static jmethodID allowsNavigation, onLoadStarted, onLoadFinished, onLoadFailed, onScriptResult;

typedef struct Page {
    jobject events;
    int width;
    int height;
    void *window;
    void *view;
    int isClosed;
    // Scripts still running, whose answers reach the page after it may have closed: it is freed once both are done.
    int pendingScripts;
} Page;

typedef struct Task {
    void (*run)(Page *, char *);
    Page *page;
    char *text;
    struct Task *next;
} Task;

static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t wake = PTHREAD_COND_INITIALIZER;
static pthread_once_t engineStarted = PTHREAD_ONCE_INIT;
static Task *head, *tail;
// Only the engine's thread reads or writes these.
static int isGtkReady;
static int openPages;

static JNIEnv *currentEnv(void) {
    JNIEnv *env = NULL;
    if ((*javaVm)->GetEnv(javaVm, (void **) &env, JNI_VERSION_1_8) == JNI_EDETACHED) {
        (*javaVm)->AttachCurrentThreadAsDaemon(javaVm, (void **) &env, NULL);
    }
    return env;
}

/** UTF-8 into the UTF-16 the JVM holds: NewStringUTF takes its modified UTF-8, which a page's emoji is not in. */
static jstring javaString(JNIEnv *env, const char *text) {
    if (text == NULL) return NULL;
    const unsigned char *bytes = (const unsigned char *) text;
    size_t length = strlen(text);
    jchar *characters = malloc((length + 1) * sizeof(jchar));
    size_t count = 0;
    for (size_t i = 0; i < length;) {
        unsigned char first = bytes[i++];
        uint32_t point;
        int extra;
        if (first < 0x80) {
            point = first;
            extra = 0;
        } else if ((first & 0xE0) == 0xC0) {
            point = first & 0x1F;
            extra = 1;
        } else if ((first & 0xF0) == 0xE0) {
            point = first & 0x0F;
            extra = 2;
        } else if ((first & 0xF8) == 0xF0) {
            point = first & 0x07;
            extra = 3;
        } else {
            point = 0xFFFD;
            extra = 0;
        }
        for (int k = 0; k < extra; k++) {
            if (i < length && (bytes[i] & 0xC0) == 0x80) {
                point = (point << 6) | (bytes[i++] & 0x3F);
            } else {
                point = 0xFFFD;
                break;
            }
        }
        if (point >= 0x10000 && point <= 0x10FFFF) {
            point -= 0x10000;
            characters[count++] = (jchar) (0xD800 + (point >> 10));
            characters[count++] = (jchar) (0xDC00 + (point & 0x3FF));
        } else {
            characters[count++] = (jchar) point;
        }
    }
    jstring result = (*env)->NewString(env, characters, (jsize) count);
    free(characters);
    return result;
}

static void failed(Page *page, const char *domain, int code) {
    if (page->isClosed) return;
    JNIEnv *env = currentEnv();
    jstring string = javaString(env, domain);
    (*env)->CallVoidMethod(env, page->events, onLoadFailed, string, (jint) code);
    (*env)->ExceptionClear(env);
    (*env)->DeleteLocalRef(env, string);
}

static void notify(Page *page, jmethodID method) {
    if (page->isClosed) return;
    JNIEnv *env = currentEnv();
    (*env)->CallVoidMethod(env, page->events, method);
    (*env)->ExceptionClear(env);
}

static void answer(Page *page, const char *result) {
    if (page->isClosed) return;
    JNIEnv *env = currentEnv();
    jstring string = javaString(env, result);
    (*env)->CallVoidMethod(env, page->events, onScriptResult, string);
    (*env)->ExceptionClear(env);
    if (string != NULL) (*env)->DeleteLocalRef(env, string);
}

static void releaseIfDone(Page *page) {
    if (page->isClosed && page->pendingScripts == 0) free(page);
}

/** Whether [response] asks to be saved as a file (`Content-Disposition: attachment`) rather than shown. */
static int isAttachment(void *response) {
    // A data: or about: response has no header table, and is shown.
    void *headers = response != NULL ? gtk.webkit_uri_response_get_http_headers(response) : NULL;
    const char *disposition = headers != NULL ? gtk.soup_message_headers_get_one(headers, "Content-Disposition") : NULL;
    if (disposition == NULL) return 0;
    while (*disposition == ' ' || *disposition == '\t') disposition++;
    return strncasecmp(disposition, "attachment", 10) == 0;
}

static int decidePolicy(void *view, void *decision, int type, void *data) {
    Page *page = data;
    if (type == NEW_WINDOW_ACTION) {
        // A window the page opens would be drawn nowhere; it is refused.
        gtk.webkit_policy_decision_ignore(decision);
        return 1;
    }
    if (type == RESPONSE_ACTION) {
        // WebKitGTK's own answer downloads what the view cannot show, or what asks to be saved, into the user's Downloads
        // folder; the reader keeps nothing, so it is ignored. An ignored page fails its load with a policy error, which
        // reads as a page that could not be read.
        int isShown = gtk.webkit_response_policy_decision_is_mime_type_supported(decision)
            && !isAttachment(gtk.webkit_response_policy_decision_get_response(decision));
        if (isShown) gtk.webkit_policy_decision_use(decision); else gtk.webkit_policy_decision_ignore(decision);
        return 1;
    }
    if (type != NAVIGATION_ACTION) return 0;
    void *action = gtk.webkit_navigation_policy_decision_get_navigation_action(decision);
    const char *address = gtk.webkit_uri_request_get_uri(gtk.webkit_navigation_action_get_request(action));
    int isAllowed = 0;
    if (!page->isClosed && address != NULL) {
        JNIEnv *env = currentEnv();
        jstring string = javaString(env, address);
        // WebKitGTK does not say which frame a navigation is in, so every one is asked as a frame's: a main frame that
        // goes to about:, data:, or blob: reaches no host, and its address is not a public one, so it is not read.
        isAllowed = (*env)->CallBooleanMethod(env, page->events, allowsNavigation, string, JNI_FALSE);
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
            isAllowed = 0;
        }
        (*env)->DeleteLocalRef(env, string);
    }
    if (isAllowed) gtk.webkit_policy_decision_use(decision); else gtk.webkit_policy_decision_ignore(decision);
    return 1;
}

static void loadChanged(void *view, int event, void *data) {
    if (event == LOAD_STARTED) notify(data, onLoadStarted);
    if (event == LOAD_FINISHED) notify(data, onLoadFinished);
}

static int loadFailed(void *view, int event, const char *address, GError *error, void *data) {
    failed(data, error != NULL ? gtk.g_quark_to_string(error->domain) : "WebKitGtk", error != NULL ? error->code : -1);
    // Handled, so WebKit draws no error page of its own.
    return 1;
}

static void webProcessTerminated(void *view, int reason, void *data) {
    failed(data, "WebKitWebProcess", reason);
}

/** A dialog the page opens is answered at once, as dismissed, rather than drawn. */
static int scriptDialog(void *view, void *dialog, void *data) {
    return 1;
}

/** A download started any other way (a script's `<a download>`) is cancelled before it picks a file to write. */
static void downloadStarted(void *context, void *download, void *data) {
    gtk.webkit_download_cancel(download);
}

static void connectSignal(void *instance, const char *signal, GCallback callback, Page *page) {
    gtk.g_signal_connect_data(instance, signal, callback, page, NULL, 0);
}

static void build(Page *page, char *unused) {
    if (!isGtkReady) {
        failed(page, "GtkInit", -1);
        return;
    }
    void *context = gtk.webkit_web_context_new_ephemeral();
    connectSignal(context, "download-started", (GCallback) downloadStarted, page);
    page->view = gtk.webkit_web_view_new_with_context(context);
    gtk.g_object_unref(context);
    void *settings = gtk.webkit_web_view_get_settings(page->view);
    gtk.webkit_settings_set_enable_javascript(settings, 1);
    gtk.webkit_settings_set_auto_load_images(settings, 0);
    connectSignal(page->view, "decide-policy", (GCallback) decidePolicy, page);
    connectSignal(page->view, "load-changed", (GCallback) loadChanged, page);
    connectSignal(page->view, "load-failed", (GCallback) loadFailed, page);
    connectSignal(page->view, "web-process-terminated", (GCallback) webProcessTerminated, page);
    connectSignal(page->view, "script-dialog", (GCallback) scriptDialog, page);
    page->window = gtk.gtk_offscreen_window_new();
    gtk.gtk_widget_set_size_request(page->window, page->width, page->height);
    gtk.gtk_container_add(page->window, page->view);
    gtk.gtk_widget_show_all(page->window);
    openPages++;
}

static void openPage(Page *page, char *address) {
    if (page->view == NULL || page->isClosed) return;
    gtk.webkit_web_view_load_uri(page->view, address);
}

static void scriptAnswered(void *view, void *result, void *data) {
    Page *page = data;
    GError *error = NULL;
    void *javascript = gtk.webkit_web_view_run_javascript_finish(view, result, &error);
    char *string = NULL;
    if (javascript != NULL) {
        void *value = gtk.webkit_javascript_result_get_js_value(javascript);
        if (gtk.jsc_value_is_string(value)) string = gtk.jsc_value_to_string(value);
        gtk.webkit_javascript_result_unref(javascript);
    }
    if (error != NULL) gtk.g_error_free(error);
    answer(page, string);
    if (string != NULL) gtk.g_free(string);
    page->pendingScripts--;
    releaseIfDone(page);
}

static void evaluate(Page *page, char *script) {
    if (page->isClosed) return;
    if (page->view == NULL) {
        answer(page, NULL);
        return;
    }
    page->pendingScripts++;
    gtk.webkit_web_view_run_javascript(page->view, script, NULL, scriptAnswered, page);
}

static void closePage(Page *page, char *unused) {
    if (page->view != NULL) {
        gtk.webkit_web_view_stop_loading(page->view);
        gtk.gtk_widget_destroy(page->window);
        openPages--;
    }
    page->isClosed = 1;
    (*currentEnv())->DeleteGlobalRef(currentEnv(), page->events);
    releaseIfDone(page);
}

static void *engineMain(void *unused) {
    isGtkReady = gtk.gtk_init_check(NULL, NULL);
    for (;;) {
        pthread_mutex_lock(&lock);
        while (head == NULL && openPages == 0) pthread_cond_wait(&wake, &lock);
        Task *tasks = head;
        head = tail = NULL;
        pthread_mutex_unlock(&lock);
        while (tasks != NULL) {
            Task *next = tasks->next;
            tasks->run(tasks->page, tasks->text);
            free(tasks->text);
            free(tasks);
            tasks = next;
        }
        if (openPages > 0) {
            gtk.g_main_context_iteration(NULL, 1);
        } else {
            // What the last view left behind (its web process going away) is let finish before the context is let go.
            while (gtk.g_main_context_iteration(NULL, 0)) {}
        }
    }
    return NULL;
}

static void startEngine(void) {
    pthread_t thread;
    if (pthread_create(&thread, NULL, engineMain, NULL) == 0) pthread_detach(thread);
}

/** Runs [run] on the engine's thread with a copy of [text], starting the thread the first time. */
static void runOnEngine(void (*run)(Page *, char *), Page *page, JNIEnv *env, jstring text) {
    pthread_once(&engineStarted, startEngine);
    Task *task = calloc(1, sizeof(Task));
    task->run = run;
    task->page = page;
    if (text != NULL) {
        const char *characters = (*env)->GetStringUTFChars(env, text, NULL);
        task->text = strdup(characters);
        (*env)->ReleaseStringUTFChars(env, text, characters);
    }
    pthread_mutex_lock(&lock);
    if (tail == NULL) head = task; else tail->next = task;
    tail = task;
    pthread_cond_signal(&wake);
    pthread_mutex_unlock(&lock);
    if (gtk.g_main_context_wakeup != NULL) gtk.g_main_context_wakeup(NULL);
}

/** Opens WebKitGTK and finds every function above, once; false where any is missing. */
static int loadWebKit(void) {
    static int state = 0;
    if (state != 0) return state > 0;
    const char *const libraries[] = {"libwebkit2gtk-4.1.so.0", "libwebkit2gtk-4.0.so.37"};
    void *handle = NULL;
    for (size_t i = 0; i < sizeof(libraries) / sizeof(libraries[0]) && handle == NULL; i++) {
        handle = dlopen(libraries[i], RTLD_NOW | RTLD_LOCAL);
    }
    state = -1;
    if (handle == NULL) return 0;
    void **functions = (void **) &gtk;
    for (size_t i = 0; i < sizeof(SYMBOLS) / sizeof(SYMBOLS[0]); i++) {
        // Found in WebKitGTK or the libraries it brings, GTK, GLib, and JavaScriptCore among them.
        functions[i] = dlsym(handle, SYMBOLS[i]);
        if (functions[i] == NULL) return 0;
    }
    state = 1;
    return 1;
}

#define BINDING(name) JNICALL Java_com_pandulapeter_campfire_data_source_remote_implementation_page_NativeWebView_##name

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    javaVm = vm;
    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_8) != JNI_OK) return JNI_ERR;
    jclass events = (*env)->FindClass(env, "com/pandulapeter/campfire/data/source/remote/implementation/page/NativeWebViewEvents");
    if (events == NULL) return JNI_ERR;
    allowsNavigation = (*env)->GetMethodID(env, events, "allowsNavigation", "(Ljava/lang/String;Z)Z");
    onLoadStarted = (*env)->GetMethodID(env, events, "onLoadStarted", "()V");
    onLoadFinished = (*env)->GetMethodID(env, events, "onLoadFinished", "()V");
    onLoadFailed = (*env)->GetMethodID(env, events, "onLoadFailed", "(Ljava/lang/String;I)V");
    onScriptResult = (*env)->GetMethodID(env, events, "onScriptResult", "(Ljava/lang/String;)V");
    if (!allowsNavigation || !onLoadStarted || !onLoadFinished || !onLoadFailed || !onScriptResult) return JNI_ERR;
    return JNI_VERSION_1_8;
}

JNIEXPORT jboolean BINDING(isEngineAvailable)(JNIEnv *env, jclass type) {
    // A session without a display (a server, a container) has nothing GTK can start on.
    int hasDisplay = getenv("DISPLAY") != NULL || getenv("WAYLAND_DISPLAY") != NULL;
    return hasDisplay && loadWebKit() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong BINDING(create)(JNIEnv *env, jclass type, jobject events, jint width, jint height, jstring dataDirectory) {
    Page *page = calloc(1, sizeof(Page));
    page->events = (*env)->NewGlobalRef(env, events);
    page->width = width;
    page->height = height;
    runOnEngine(build, page, env, NULL);
    return (jlong) (intptr_t) page;
}

JNIEXPORT void BINDING(open)(JNIEnv *env, jclass type, jlong view, jstring address) {
    runOnEngine(openPage, (Page *) (intptr_t) view, env, address);
}

JNIEXPORT void BINDING(evaluate)(JNIEnv *env, jclass type, jlong view, jstring script) {
    runOnEngine(evaluate, (Page *) (intptr_t) view, env, script);
}

JNIEXPORT void BINDING(close)(JNIEnv *env, jclass type, jlong view) {
    runOnEngine(closePage, (Page *) (intptr_t) view, env, NULL);
}
