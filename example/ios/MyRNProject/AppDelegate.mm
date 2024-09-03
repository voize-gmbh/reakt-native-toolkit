#import "AppDelegate.h"

#import <React/RCTBundleURLProvider.h>
#import <shared/shared.h>
#import "ReactNativeViewManagers.h"

@implementation AppDelegate

// overwrites the parent (RCTAppDelegate) createBridgeWithDelegate to rewire the delegate to this class so that extraModulesForBridge is picked up
- (RCTBridge *)createBridgeWithDelegate:(id<RCTBridgeDelegate>)delegate launchOptions:(NSDictionary *)launchOptions
{
  return [[RCTBridge alloc] initWithDelegate:self launchOptions:launchOptions];
}

- (NSArray<id<RCTBridgeModule>> *)extraModulesForBridge:(RCTBridge *)bridge
{
  SharedIOSRNModules* iOSRNModules = [[SharedIOSRNModules alloc] init];
  NSArray<id<RCTBridgeModule>> *rnNativeModules = [iOSRNModules createNativeModules];
  NSArray<id<RCTBridgeModule>> *rnViewManagers = [ReactNativeViewManagers getRNViewManagers:[iOSRNModules createViewManagers]];
  return [rnNativeModules arrayByAddingObjectsFromArray:rnViewManagers];
}

- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)launchOptions
{
  self.moduleName = @"MyRNProject";
  // You can add your custom initial props in the dictionary below.
  // They will be passed down to the ViewController used by React Native.
  self.initialProps = @{};

  return [super application:application didFinishLaunchingWithOptions:launchOptions];
}

- (NSURL *)sourceURLForBridge:(RCTBridge *)bridge
{
  return [self bundleURL];
}

- (NSURL *)bundleURL
{
#if DEBUG
  return [[RCTBundleURLProvider sharedSettings] jsBundleURLForBundleRoot:@"index"];
#else
  return [[NSBundle mainBundle] URLForResource:@"main" withExtension:@"jsbundle"];
#endif
}

@end
