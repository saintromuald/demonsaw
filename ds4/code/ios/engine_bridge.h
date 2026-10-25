#ifndef _DEMONSAW_ENGINE_BRIDGE_H_
#define _DEMONSAW_ENGINE_BRIDGE_H_

// C ABI for the headless Demonsaw engine, used by the iOS Swift app.
// The Swift side funnels every call through one serial queue, and the
// engine itself is single-threaded (same contract as the Android JNI
// layer), so no locks are needed here.
//
// Strings returned by demonsaw_snapshot/demonsaw_command are allocated
// with malloc() and must be released with demonsaw_free().

#ifdef __cplusplus
extern "C" {
#endif

int demonsaw_start(const char* config_path);
void demonsaw_stop(void);
const char* demonsaw_snapshot(void);
const char* demonsaw_command(int type, const char* a, const char* b);
int demonsaw_is_running(void);
void demonsaw_free(void* ptr);

#ifdef __cplusplus
}
#endif

#endif
