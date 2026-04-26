#import <Foundation/Foundation.h>

// Auto-registers the native AVIF handler when the library binary is loaded.
// Uses __attribute__((constructor)) which runs after all ObjC classes are
// registered but before main() — no manual setup needed by consumers.
//
// This replaces Swift's +load (which is no longer allowed in modern Swift).
// The same pattern is used by Firebase, Sentry, and other popular iOS SDKs.

__attribute__((constructor))
static void AvifKitAutoRegister(void) {
    Class cls = NSClassFromString(@"AvifKitSetup");
    if (cls) {
        SEL sel = NSSelectorFromString(@"registerNativeHandler");
        if ([cls respondsToSelector:sel]) {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Warc-performSelector-leaks"
            [cls performSelector:sel];
#pragma clang diagnostic pop
        }
    }
}
