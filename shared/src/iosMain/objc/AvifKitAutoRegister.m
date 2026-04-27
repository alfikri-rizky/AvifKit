#import <Foundation/Foundation.h>

// Auto-registers the native AVIF handler when the library binary is loaded.
// Uses __attribute__((constructor)) which runs after all ObjC/Swift +load
// methods — so Swift classes are fully registered before this executes.
//
// Swift classes in SPM modules may be registered under two different ObjC names:
//   1. "AvifKitSetup"         — when Swift strips the module prefix for ObjC
//   2. "AvifKit.AvifKitSetup" — when Swift preserves the module prefix
// We try both to cover all cases.

__attribute__((constructor))
static void AvifKitAutoRegister(void) {
    // Try with and without module prefix (Swift SPM behavior varies by Xcode version)
    Class cls = NSClassFromString(@"AvifKit.AvifKitSetup");
    if (!cls) {
        cls = NSClassFromString(@"AvifKitSetup");
    }

    if (cls) {
        SEL sel = NSSelectorFromString(@"registerNativeHandler");
        if ([cls respondsToSelector:sel]) {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Warc-performSelector-leaks"
            [cls performSelector:sel];
#pragma clang diagnostic pop
            NSLog(@"[AvifKit] ✅ Native handler auto-registered successfully");
        } else {
            NSLog(@"[AvifKit] ⚠️ AvifKitSetup found but registerNativeHandler selector not found");
        }
    } else {
        // This is expected when AvifKit Swift target is not yet loaded.
        // The consumer must call AvifKitSetup.registerNativeHandler() manually.
        NSLog(@"[AvifKit] ℹ️ AvifKitSetup class not found — call AvifKitSetup.registerNativeHandler() in your App init()");
    }
}
