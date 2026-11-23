/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

// The macOS web view helper (NativeWebView.kt): a WKWebView in no window, with a non-persistent data store.
//
// WebKit's views live on the main thread, which on macOS is AppKit's: the JVM runs on threads of its own, and AWT runs
// NSApplication's loop on the first thread, where Compose Desktop's event thread is not. Every call from the JVM is
// therefore queued onto the main dispatch queue and returns at once, and every answer comes back from the main thread
// through the view's NativeWebViewEvents. Nothing here waits for the main thread, so a JVM thread the main thread is
// itself waiting for (AWT's invokeAndWait) can never deadlock with it.

#import <Foundation/Foundation.h>
#import <WebKit/WebKit.h>
#include <jni.h>

static JavaVM *javaVm;
static jmethodID allowsNavigation, onLoadStarted, onLoadFinished, onLoadFailed, onScriptResult;

// Pictures and media are refused, and so is anything plain http: or ws: the page asks for. WebKit refuses a
// subresource only through a content rule list, which has no way to say "a public host", so the local network is
// refused for navigations alone, as in the iOS app. A rule's filter takes no disjunction, hence a rule per scheme.
// The iOS reader compiles the same list (WebKitBrowser.kt); keep the two in step.
static NSString *const RULES =
    @"[{\"trigger\":{\"url-filter\":\".*\",\"resource-type\":[\"image\",\"media\"]},\"action\":{\"type\":\"block\"}},"
    @"{\"trigger\":{\"url-filter\":\"^http:\"},\"action\":{\"type\":\"block\"}},"
    @"{\"trigger\":{\"url-filter\":\"^ws:\"},\"action\":{\"type\":\"block\"}}]";

static JNIEnv *currentEnv(void) {
    JNIEnv *env = NULL;
    if ((*javaVm)->GetEnv(javaVm, (void **) &env, JNI_VERSION_1_8) == JNI_EDETACHED) {
        (*javaVm)->AttachCurrentThreadAsDaemon(javaVm, (void **) &env, NULL);
    }
    return env;
}

/** As UTF-16, which both sides hold: NewStringUTF takes the JVM's modified UTF-8, which a page's emoji is not in. */
static jstring javaString(JNIEnv *env, NSString *string) {
    if (string == nil) return NULL;
    NSUInteger length = string.length;
    unichar *characters = malloc(MAX(length, 1) * sizeof(unichar));
    [string getCharacters:characters range:NSMakeRange(0, length)];
    jstring result = (*env)->NewString(env, characters, (jsize) length);
    free(characters);
    return result;
}

static NSString *cocoaString(JNIEnv *env, jstring string) {
    const jchar *characters = (*env)->GetStringChars(env, string, NULL);
    NSString *result = [NSString stringWithCharacters:characters length:(NSUInteger) (*env)->GetStringLength(env, string)];
    (*env)->ReleaseStringChars(env, string, characters);
    return result;
}

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

/** The content rule list every view shares, compiled once, or nil where it could not be (the views go without it). */
static WKContentRuleList *ruleList;
static BOOL isRuleListSettled;
static NSMutableArray<dispatch_block_t> *waitingForRuleList;

/** Runs [block] on the main thread once the rule list is settled. */
static void withRuleList(dispatch_block_t block) {
    if (isRuleListSettled) {
        block();
        return;
    }
    if (waitingForRuleList != nil) {
        [waitingForRuleList addObject:block];
        return;
    }
    waitingForRuleList = [NSMutableArray arrayWithObject:block];
    // A store of its own in the temporary folder: the default store would keep the compiled list in the user's
    // WebKit folder for good, though it holds nothing of any page.
    NSURL *folder = [NSURL fileURLWithPath:[NSTemporaryDirectory() stringByAppendingPathComponent:@"CampfireWebViewRules"]];
    WKContentRuleListStore *store = [WKContentRuleListStore storeWithURL:folder];
    [store compileContentRuleListForIdentifier:@"campfire" encodedContentRuleList:RULES completionHandler:^(WKContentRuleList *list, NSError *error) {
        dispatch_async(dispatch_get_main_queue(), ^{
            ruleList = list;
            isRuleListSettled = YES;
            NSArray<dispatch_block_t> *blocks = waitingForRuleList;
            waitingForRuleList = nil;
            for (dispatch_block_t waiting in blocks) waiting();
        });
    }];
}

@interface CampfirePage : NSObject <WKNavigationDelegate>
@end

@implementation CampfirePage {
    jobject events;
    WKWebView *webView;
    NSString *pendingAddress;
    BOOL isClosed;
}

- (instancetype)initWithEvents:(jobject)pageEvents {
    if ((self = [super init])) events = pageEvents;
    return self;
}

- (void)buildWithWidth:(CGFloat)width height:(CGFloat)height {
    if (isClosed) return;
    WKWebViewConfiguration *configuration = [WKWebViewConfiguration new];
    configuration.websiteDataStore = [WKWebsiteDataStore nonPersistentDataStore];
    if (ruleList != nil) [configuration.userContentController addContentRuleList:ruleList];
    webView = [[WKWebView alloc] initWithFrame:NSMakeRect(0, 0, width, height) configuration:configuration];
    webView.navigationDelegate = self;
    if (pendingAddress != nil) [self open:pendingAddress];
    pendingAddress = nil;
}

- (void)open:(NSString *)address {
    if (isClosed) return;
    if (webView == nil) {
        pendingAddress = address;
        return;
    }
    NSURL *url = [NSURL URLWithString:address];
    if (url == nil) {
        [self failedIn:@"NSURLErrorDomain" code:NSURLErrorBadURL];
        return;
    }
    [webView loadRequest:[NSURLRequest requestWithURL:url]];
}

- (void)evaluate:(NSString *)script {
    if (isClosed) return;
    if (webView == nil) {
        [self answer:nil];
        return;
    }
    [webView evaluateJavaScript:script completionHandler:^(id result, NSError *error) {
        [self answer:[result isKindOfClass:[NSString class]] ? result : nil];
    }];
}

- (void)close {
    if (isClosed) return;
    isClosed = YES;
    [webView stopLoading];
    webView.navigationDelegate = nil;
    webView = nil;
    (*currentEnv())->DeleteGlobalRef(currentEnv(), events);
    events = NULL;
}

- (void)answer:(NSString *)result {
    if (isClosed) return;
    JNIEnv *env = currentEnv();
    jstring string = javaString(env, result);
    (*env)->CallVoidMethod(env, events, onScriptResult, string);
    (*env)->ExceptionClear(env);
    if (string != NULL) (*env)->DeleteLocalRef(env, string);
}

- (void)failedIn:(NSString *)domain code:(NSInteger)code {
    if (isClosed) return;
    JNIEnv *env = currentEnv();
    jstring string = javaString(env, domain);
    (*env)->CallVoidMethod(env, events, onLoadFailed, string, (jint) code);
    (*env)->ExceptionClear(env);
    (*env)->DeleteLocalRef(env, string);
}

- (void)webView:(WKWebView *)view decidePolicyForNavigationAction:(WKNavigationAction *)action decisionHandler:(void (^)(WKNavigationActionPolicy))decisionHandler {
    BOOL isAllowed = NO;
    NSString *address = action.request.URL.absoluteString;
    if (!isClosed && address != nil) {
        JNIEnv *env = currentEnv();
        jstring string = javaString(env, address);
        // A navigation with no frame of its own (a new window) is the page's.
        BOOL isMainFrame = action.targetFrame == nil || action.targetFrame.isMainFrame;
        isAllowed = (*env)->CallBooleanMethod(env, events, allowsNavigation, string, (jboolean) isMainFrame);
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
            isAllowed = NO;
        }
        (*env)->DeleteLocalRef(env, string);
    }
    decisionHandler(isAllowed ? WKNavigationActionPolicyAllow : WKNavigationActionPolicyCancel);
}

- (void)webView:(WKWebView *)view didStartProvisionalNavigation:(WKNavigation *)navigation {
    if (isClosed) return;
    JNIEnv *env = currentEnv();
    (*env)->CallVoidMethod(env, events, onLoadStarted);
    (*env)->ExceptionClear(env);
}

- (void)webView:(WKWebView *)view didFinishNavigation:(WKNavigation *)navigation {
    if (isClosed) return;
    JNIEnv *env = currentEnv();
    (*env)->CallVoidMethod(env, events, onLoadFinished);
    (*env)->ExceptionClear(env);
}

- (void)webView:(WKWebView *)view didFailProvisionalNavigation:(WKNavigation *)navigation withError:(NSError *)error {
    [self failedIn:error.domain code:error.code];
}

- (void)webView:(WKWebView *)view didFailNavigation:(WKNavigation *)navigation withError:(NSError *)error {
    [self failedIn:error.domain code:error.code];
}

- (void)webViewWebContentProcessDidTerminate:(WKWebView *)view {
    [self failedIn:@"WebContentProcess" code:0];
}

@end

static CampfirePage *pageOf(jlong view) {
    return (__bridge CampfirePage *) (void *) (intptr_t) view;
}

#define BINDING(name) Java_com_pandulapeter_campfire_data_source_remote_implementation_page_NativeWebView_##name

JNIEXPORT jboolean JNICALL BINDING(isEngineAvailable)(JNIEnv *env, jclass type) {
    return JNI_TRUE;
}

JNIEXPORT jlong JNICALL BINDING(create)(JNIEnv *env, jclass type, jobject events, jint width, jint height, jstring dataDirectory) {
    CampfirePage *page = [[CampfirePage alloc] initWithEvents:(*env)->NewGlobalRef(env, events)];
    dispatch_async(dispatch_get_main_queue(), ^{
        withRuleList(^{ [page buildWithWidth:width height:height]; });
    });
    return (jlong) (intptr_t) CFBridgingRetain(page);
}

JNIEXPORT void JNICALL BINDING(open)(JNIEnv *env, jclass type, jlong view, jstring address) {
    CampfirePage *page = pageOf(view);
    NSString *string = cocoaString(env, address);
    dispatch_async(dispatch_get_main_queue(), ^{ [page open:string]; });
}

JNIEXPORT void JNICALL BINDING(evaluate)(JNIEnv *env, jclass type, jlong view, jstring script) {
    CampfirePage *page = pageOf(view);
    NSString *string = cocoaString(env, script);
    dispatch_async(dispatch_get_main_queue(), ^{ [page evaluate:string]; });
}

JNIEXPORT void JNICALL BINDING(close)(JNIEnv *env, jclass type, jlong view) {
    // The handle's own reference is handed to the block, which lets go of the page once it is closed.
    CampfirePage *page = CFBridgingRelease((CFTypeRef) (intptr_t) view);
    dispatch_async(dispatch_get_main_queue(), ^{ [page close]; });
}
