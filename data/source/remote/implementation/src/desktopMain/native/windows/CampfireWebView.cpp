/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

// The Windows web view helper (NativeWebView.kt): WebView2 in a window that is never shown, InPrivate, so no cookie,
// cache, or storage outlives the view.
//
// WebView2 needs a single-threaded apartment with a message loop, which no JVM thread has (Compose Desktop's event
// thread is AWT's, and must not be blocked). The helper starts a thread of its own on first use, which joins an STA,
// keeps a message-only window, and runs its loop for the life of the process; every call from the JVM is posted to
// that window and returns at once, and every answer comes back from that thread through the view's NativeWebViewEvents.
// The WebView2 loader is linked in statically, so nothing but the runtime the system has is needed; where it is
// missing, isEngineAvailable says so and pages are not read.

#include <windows.h>
#include <wrl.h>
#include <jni.h>
#include <functional>
#include <memory>
#include <mutex>
#include <string>
#include "WebView2.h"

using Microsoft::WRL::Callback;
using Microsoft::WRL::ComPtr;

namespace {

JavaVM *javaVm;
jmethodID allowsNavigation, allowsResource, onLoadStarted, onLoadFinished, onLoadFailed, onScriptResult;

/** What NativeLoadFailure reads as the page's failure: the engine could not start, or its process went away. */
constexpr int ENGINE_FAILED = -1;

constexpr UINT RUN_TASK = WM_APP + 1;
constexpr wchar_t DISPATCHER_CLASS[] = L"CampfireWebViewDispatcher";
constexpr wchar_t HOST_CLASS[] = L"CampfireWebViewHost";

HMODULE module;
HWND dispatcher;
std::once_flag engineStarted;

JNIEnv *currentEnv() {
    JNIEnv *env = nullptr;
    if (javaVm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_8) == JNI_EDETACHED) {
        javaVm->AttachCurrentThreadAsDaemon(reinterpret_cast<void **>(&env), nullptr);
    }
    return env;
}

std::wstring wideString(JNIEnv *env, jstring string) {
    const jchar *characters = env->GetStringChars(string, nullptr);
    std::wstring result(reinterpret_cast<const wchar_t *>(characters), env->GetStringLength(string));
    env->ReleaseStringChars(string, characters);
    return result;
}

jstring javaString(JNIEnv *env, const wchar_t *string) {
    return string == nullptr ? nullptr : env->NewString(reinterpret_cast<const jchar *>(string), static_cast<jsize>(wcslen(string)));
}

struct Page {
    jobject events;
    int width;
    int height;
    std::wstring dataDirectory;
    std::wstring pendingAddress;
    HWND host = nullptr;
    ComPtr<ICoreWebView2Environment> environment;
    ComPtr<ICoreWebView2Controller> controller;
    ComPtr<ICoreWebView2> webView;
    bool isClosed = false;
};

using PageRef = std::shared_ptr<Page>;

void failed(const PageRef &page, int code) {
    if (page->isClosed) return;
    JNIEnv *env = currentEnv();
    jstring domain = javaString(env, L"WebView2");
    env->CallVoidMethod(page->events, onLoadFailed, domain, static_cast<jint>(code));
    env->ExceptionClear();
    env->DeleteLocalRef(domain);
}

void notify(const PageRef &page, jmethodID method) {
    if (page->isClosed) return;
    JNIEnv *env = currentEnv();
    env->CallVoidMethod(page->events, method);
    env->ExceptionClear();
}

void answer(const PageRef &page, const wchar_t *result) {
    if (page->isClosed) return;
    JNIEnv *env = currentEnv();
    jstring string = javaString(env, result);
    env->CallVoidMethod(page->events, onScriptResult, string);
    env->ExceptionClear();
    if (string != nullptr) env->DeleteLocalRef(string);
}

/** Asks the view's events whether [address] may be loaded; anything that goes wrong asking is a refusal. */
bool allows(const PageRef &page, jmethodID method, const wchar_t *address, bool flag) {
    if (page->isClosed || address == nullptr) return false;
    JNIEnv *env = currentEnv();
    jstring string = javaString(env, address);
    bool isAllowed = env->CallBooleanMethod(page->events, method, string, static_cast<jboolean>(flag));
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        isAllowed = false;
    }
    env->DeleteLocalRef(string);
    return isAllowed;
}

void navigate(const PageRef &page) {
    if (page->webView == nullptr || page->pendingAddress.empty()) return;
    if (FAILED(page->webView->Navigate(page->pendingAddress.c_str()))) failed(page, ENGINE_FAILED);
    page->pendingAddress.clear();
}

void listenTo(const PageRef &page) {
    ICoreWebView2 *webView = page->webView.Get();
    EventRegistrationToken token;
    webView->add_NavigationStarting(Callback<ICoreWebView2NavigationStartingEventHandler>(
        [page](ICoreWebView2 *, ICoreWebView2NavigationStartingEventArgs *args) -> HRESULT {
            LPWSTR uri = nullptr;
            args->get_Uri(&uri);
            bool isAllowed = allows(page, allowsNavigation, uri, true);
            CoTaskMemFree(uri);
            if (isAllowed) notify(page, onLoadStarted); else args->put_Cancel(TRUE);
            return S_OK;
        }).Get(), &token);
    webView->add_FrameNavigationStarting(Callback<ICoreWebView2NavigationStartingEventHandler>(
        [page](ICoreWebView2 *, ICoreWebView2NavigationStartingEventArgs *args) -> HRESULT {
            LPWSTR uri = nullptr;
            args->get_Uri(&uri);
            if (!allows(page, allowsNavigation, uri, false)) args->put_Cancel(TRUE);
            CoTaskMemFree(uri);
            return S_OK;
        }).Get(), &token);
    webView->add_NavigationCompleted(Callback<ICoreWebView2NavigationCompletedEventHandler>(
        [page](ICoreWebView2 *, ICoreWebView2NavigationCompletedEventArgs *args) -> HRESULT {
            BOOL isSuccess = FALSE;
            args->get_IsSuccess(&isSuccess);
            if (isSuccess) {
                notify(page, onLoadFinished);
            } else {
                COREWEBVIEW2_WEB_ERROR_STATUS status = COREWEBVIEW2_WEB_ERROR_STATUS_UNKNOWN;
                args->get_WebErrorStatus(&status);
                failed(page, static_cast<int>(status));
            }
            return S_OK;
        }).Get(), &token);
    webView->add_ProcessFailed(Callback<ICoreWebView2ProcessFailedEventHandler>(
        [page](ICoreWebView2 *, ICoreWebView2ProcessFailedEventArgs *) -> HRESULT {
            failed(page, ENGINE_FAILED);
            return S_OK;
        }).Get(), &token);
    // A window the page opens would be shown on the user's screen; it is refused.
    webView->add_NewWindowRequested(Callback<ICoreWebView2NewWindowRequestedEventHandler>(
        [](ICoreWebView2 *, ICoreWebView2NewWindowRequestedEventArgs *args) -> HRESULT {
            args->put_Handled(TRUE);
            return S_OK;
        }).Get(), &token);
    // With the default dialogs off, a dialog nobody accepts is dismissed, so an alert never waits for an answer.
    webView->add_ScriptDialogOpening(Callback<ICoreWebView2ScriptDialogOpeningEventHandler>(
        [](ICoreWebView2 *, ICoreWebView2ScriptDialogOpeningEventArgs *) -> HRESULT { return S_OK; }).Get(), &token);
    webView->add_PermissionRequested(Callback<ICoreWebView2PermissionRequestedEventHandler>(
        [](ICoreWebView2 *, ICoreWebView2PermissionRequestedEventArgs *args) -> HRESULT {
            args->put_State(COREWEBVIEW2_PERMISSION_STATE_DENY);
            return S_OK;
        }).Get(), &token);
    // Every request the page makes, its frames' and workers' too where the runtime can say, is asked about: pictures
    // and media are refused, and so is anything off a public https host.
    ComPtr<ICoreWebView2_22> webView22;
    if (SUCCEEDED(page->webView.As(&webView22))) {
        webView22->AddWebResourceRequestedFilterWithRequestSourceKinds(
            L"*", COREWEBVIEW2_WEB_RESOURCE_CONTEXT_ALL, COREWEBVIEW2_WEB_RESOURCE_REQUEST_SOURCE_KINDS_ALL);
    } else {
        webView->AddWebResourceRequestedFilter(L"*", COREWEBVIEW2_WEB_RESOURCE_CONTEXT_ALL);
    }
    webView->add_WebResourceRequested(Callback<ICoreWebView2WebResourceRequestedEventHandler>(
        [page](ICoreWebView2 *, ICoreWebView2WebResourceRequestedEventArgs *args) -> HRESULT {
            ComPtr<ICoreWebView2WebResourceRequest> request;
            args->get_Request(&request);
            LPWSTR uri = nullptr;
            request->get_Uri(&uri);
            COREWEBVIEW2_WEB_RESOURCE_CONTEXT context = COREWEBVIEW2_WEB_RESOURCE_CONTEXT_ALL;
            args->get_ResourceContext(&context);
            bool isPicture = context == COREWEBVIEW2_WEB_RESOURCE_CONTEXT_IMAGE || context == COREWEBVIEW2_WEB_RESOURCE_CONTEXT_MEDIA;
            bool isAllowed = allows(page, allowsResource, uri, isPicture);
            CoTaskMemFree(uri);
            if (!isAllowed && page->environment != nullptr) {
                ComPtr<ICoreWebView2WebResourceResponse> response;
                page->environment->CreateWebResourceResponse(nullptr, 403, L"Refused", L"", &response);
                args->put_Response(response.Get());
            }
            return S_OK;
        }).Get(), &token);
    ComPtr<ICoreWebView2_4> webView4;
    if (SUCCEEDED(page->webView.As(&webView4))) {
        webView4->add_DownloadStarting(Callback<ICoreWebView2DownloadStartingEventHandler>(
            [](ICoreWebView2 *, ICoreWebView2DownloadStartingEventArgs *args) -> HRESULT {
                args->put_Cancel(TRUE);
                return S_OK;
            }).Get(), &token);
    }
}

void setUp(const PageRef &page, ICoreWebView2Controller *controller) {
    page->controller = controller;
    controller->get_CoreWebView2(&page->webView);
    RECT bounds = {0, 0, page->width, page->height};
    controller->put_Bounds(bounds);
    // Visible inside a window that never is, so the page draws and runs its timers as in a browser tab in front.
    controller->put_IsVisible(TRUE);
    ComPtr<ICoreWebView2Settings> settings;
    page->webView->get_Settings(&settings);
    settings->put_IsScriptEnabled(TRUE);
    settings->put_AreDefaultScriptDialogsEnabled(FALSE);
    settings->put_IsWebMessageEnabled(FALSE);
    settings->put_AreDevToolsEnabled(FALSE);
    settings->put_AreDefaultContextMenusEnabled(FALSE);
    settings->put_AreHostObjectsAllowed(FALSE);
    settings->put_IsStatusBarEnabled(FALSE);
    listenTo(page);
    navigate(page);
}

void build(const PageRef &page) {
    page->host = CreateWindowExW(0, HOST_CLASS, L"", WS_POPUP, 0, 0, page->width, page->height, nullptr, nullptr, module, nullptr);
    HRESULT started = CreateCoreWebView2EnvironmentWithOptions(nullptr, page->dataDirectory.c_str(), nullptr,
        Callback<ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler>(
            [page](HRESULT result, ICoreWebView2Environment *environment) -> HRESULT {
                ComPtr<ICoreWebView2Environment10> environment10;
                if (page->isClosed) return S_OK;
                if (FAILED(result) || environment == nullptr || FAILED(environment->QueryInterface(IID_PPV_ARGS(&environment10)))) {
                    failed(page, ENGINE_FAILED);
                    return S_OK;
                }
                page->environment = environment;
                ComPtr<ICoreWebView2ControllerOptions> options;
                if (FAILED(environment10->CreateCoreWebView2ControllerOptions(&options)) ||
                    FAILED(options->put_IsInPrivateModeEnabled(TRUE))) {
                    failed(page, ENGINE_FAILED);
                    return S_OK;
                }
                HRESULT created = environment10->CreateCoreWebView2ControllerWithOptions(page->host, options.Get(),
                    Callback<ICoreWebView2CreateCoreWebView2ControllerCompletedHandler>(
                        [page](HRESULT result, ICoreWebView2Controller *controller) -> HRESULT {
                            if (page->isClosed) {
                                if (controller != nullptr) controller->Close();
                            } else if (FAILED(result) || controller == nullptr) {
                                failed(page, ENGINE_FAILED);
                            } else {
                                setUp(page, controller);
                            }
                            return S_OK;
                        }).Get());
                if (FAILED(created)) failed(page, ENGINE_FAILED);
                return S_OK;
            }).Get());
    if (FAILED(started)) failed(page, ENGINE_FAILED);
}

void closePage(const PageRef &page) {
    if (page->isClosed) return;
    page->isClosed = true;
    if (page->controller != nullptr) page->controller->Close();
    page->controller = nullptr;
    page->webView = nullptr;
    page->environment = nullptr;
    if (page->host != nullptr) DestroyWindow(page->host);
    currentEnv()->DeleteGlobalRef(page->events);
}

LRESULT CALLBACK dispatcherProcedure(HWND window, UINT message, WPARAM wParam, LPARAM lParam) {
    if (message == RUN_TASK) {
        std::unique_ptr<std::function<void()>> task(reinterpret_cast<std::function<void()> *>(lParam));
        (*task)();
        return 0;
    }
    return DefWindowProcW(window, message, wParam, lParam);
}

DWORD WINAPI engineMain(LPVOID ready) {
    CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);
    WNDCLASSW dispatcherClass = {};
    dispatcherClass.lpfnWndProc = dispatcherProcedure;
    dispatcherClass.hInstance = module;
    dispatcherClass.lpszClassName = DISPATCHER_CLASS;
    RegisterClassW(&dispatcherClass);
    WNDCLASSW hostClass = {};
    hostClass.lpfnWndProc = DefWindowProcW;
    hostClass.hInstance = module;
    hostClass.lpszClassName = HOST_CLASS;
    RegisterClassW(&hostClass);
    dispatcher = CreateWindowExW(0, DISPATCHER_CLASS, L"", 0, 0, 0, 0, 0, HWND_MESSAGE, nullptr, module, nullptr);
    SetEvent(static_cast<HANDLE>(ready));
    MSG message;
    while (GetMessageW(&message, nullptr, 0, 0) > 0) {
        TranslateMessage(&message);
        DispatchMessageW(&message);
    }
    return 0;
}

/** Runs [task] on the engine's thread, starting it the first time. */
void runOnEngine(std::function<void()> task) {
    std::call_once(engineStarted, [] {
        HANDLE ready = CreateEventW(nullptr, TRUE, FALSE, nullptr);
        HANDLE thread = CreateThread(nullptr, 0, engineMain, ready, 0, nullptr);
        if (thread != nullptr) {
            WaitForSingleObject(ready, INFINITE);
            CloseHandle(thread);
        }
        CloseHandle(ready);
    });
    if (dispatcher == nullptr) return;
    auto *heapTask = new std::function<void()>(std::move(task));
    if (!PostMessageW(dispatcher, RUN_TASK, 0, reinterpret_cast<LPARAM>(heapTask))) delete heapTask;
}

PageRef &pageOf(jlong view) {
    return *reinterpret_cast<PageRef *>(static_cast<intptr_t>(view));
}

} // namespace

#define BINDING(name) JNICALL Java_com_pandulapeter_campfire_data_source_remote_implementation_page_NativeWebView_##name

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *) {
    javaVm = vm;
    GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS | GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
        reinterpret_cast<LPCWSTR>(&engineMain), &module);
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_8) != JNI_OK) return JNI_ERR;
    jclass events = env->FindClass("com/pandulapeter/campfire/data/source/remote/implementation/page/NativeWebViewEvents");
    if (events == nullptr) return JNI_ERR;
    allowsNavigation = env->GetMethodID(events, "allowsNavigation", "(Ljava/lang/String;Z)Z");
    allowsResource = env->GetMethodID(events, "allowsResource", "(Ljava/lang/String;Z)Z");
    onLoadStarted = env->GetMethodID(events, "onLoadStarted", "()V");
    onLoadFinished = env->GetMethodID(events, "onLoadFinished", "()V");
    onLoadFailed = env->GetMethodID(events, "onLoadFailed", "(Ljava/lang/String;I)V");
    onScriptResult = env->GetMethodID(events, "onScriptResult", "(Ljava/lang/String;)V");
    if (!allowsNavigation || !allowsResource || !onLoadStarted || !onLoadFinished || !onLoadFailed || !onScriptResult) return JNI_ERR;
    return JNI_VERSION_1_8;
}

JNIEXPORT jboolean BINDING(isEngineAvailable)(JNIEnv *, jclass) {
    LPWSTR version = nullptr;
    HRESULT result = GetAvailableCoreWebView2BrowserVersionString(nullptr, &version);
    bool isAvailable = SUCCEEDED(result) && version != nullptr;
    CoTaskMemFree(version);
    return isAvailable ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong BINDING(create)(JNIEnv *env, jclass, jobject events, jint width, jint height, jstring dataDirectory) {
    auto page = std::make_shared<Page>();
    page->events = env->NewGlobalRef(events);
    page->width = width;
    page->height = height;
    page->dataDirectory = wideString(env, dataDirectory);
    runOnEngine([page] { build(page); });
    return static_cast<jlong>(reinterpret_cast<intptr_t>(new PageRef(page)));
}

JNIEXPORT void BINDING(open)(JNIEnv *env, jclass, jlong view, jstring address) {
    PageRef page = pageOf(view);
    std::wstring string = wideString(env, address);
    runOnEngine([page, string] {
        page->pendingAddress = string;
        navigate(page);
    });
}

JNIEXPORT void BINDING(evaluate)(JNIEnv *env, jclass, jlong view, jstring script) {
    PageRef page = pageOf(view);
    std::wstring string = wideString(env, script);
    runOnEngine([page, string] {
        if (page->webView == nullptr) {
            answer(page, nullptr);
            return;
        }
        HRESULT queued = page->webView->ExecuteScript(string.c_str(), Callback<ICoreWebView2ExecuteScriptCompletedHandler>(
            [page](HRESULT result, LPCWSTR json) -> HRESULT {
                answer(page, SUCCEEDED(result) ? json : nullptr);
                return S_OK;
            }).Get());
        if (FAILED(queued)) answer(page, nullptr);
    });
}

JNIEXPORT void BINDING(close)(JNIEnv *, jclass, jlong view) {
    PageRef *handle = &pageOf(view);
    PageRef page = *handle;
    delete handle;
    runOnEngine([page] { closePage(page); });
}

} // extern "C"
