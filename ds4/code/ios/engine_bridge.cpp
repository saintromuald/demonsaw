// C bridge between Swift and the headless Demonsaw engine.
// Mirrors the Android JNI layer (android/jni/demonsaw_jni.cpp) 1:1.

#include "ios/engine_bridge.h"

#include <cstdlib>
#include <cstring>
#include <memory>
#include <string>

#include "android/engine/engine.h"

namespace
{
	std::unique_ptr<demonsaw::engine> g_engine;

	demonsaw::engine* engine()
	{
		if (!g_engine)
			g_engine.reset(new demonsaw::engine());

		return g_engine.get();
	}

	char* strdup_c(const std::string& value)
	{
		char* copy = static_cast<char*>(std::malloc(value.size() + 1));
		if (copy)
		{
			std::memcpy(copy, value.c_str(), value.size() + 1);
		}
		return copy;
	}
}

extern "C"
{
	int demonsaw_start(const char* config_path)
	{
		if (!config_path)
			return 0;

		return engine()->start(config_path) ? 1 : 0;
	}

	void demonsaw_stop(void)
	{
		engine()->stop();
	}

	const char* demonsaw_snapshot(void)
	{
		return strdup_c(engine()->snapshot());
	}

	const char* demonsaw_command(int type, const char* a, const char* b)
	{
		return strdup_c(engine()->command(type, a ? a : "", b ? b : ""));
	}

	int demonsaw_is_running(void)
	{
		return engine()->running() ? 1 : 0;
	}

	void demonsaw_free(void* ptr)
	{
		std::free(ptr);
	}
}
