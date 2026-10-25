#include <jni.h>

#include <memory>
#include <string>

#include "android/engine/engine.h"

namespace
{
	std::unique_ptr<demonsaw::engine> g_engine;

	std::string to_string(JNIEnv* env, jstring value)
	{
		if (!value)
			return std::string();

		const char* chars = env->GetStringUTFChars(value, nullptr);
		if (!chars)
			return std::string();

		std::string result(chars);
		env->ReleaseStringUTFChars(value, chars);

		return result;
	}

	jstring to_jstring(JNIEnv* env, const std::string& value)
	{
		return env->NewStringUTF(value.c_str());
	}

	demonsaw::engine* engine()
	{
		if (!g_engine)
			g_engine.reset(new demonsaw::engine());

		return g_engine.get();
	}
}

extern "C"
{
	JNIEXPORT jboolean JNICALL Java_org_demonsaw_app_Native_start(JNIEnv* env, jobject, jstring path)
	{
		return engine()->start(to_string(env, path)) ? JNI_TRUE : JNI_FALSE;
	}

	JNIEXPORT void JNICALL Java_org_demonsaw_app_Native_stop(JNIEnv*, jobject)
	{
		engine()->stop();
	}

	JNIEXPORT jstring JNICALL Java_org_demonsaw_app_Native_snapshot(JNIEnv* env, jobject)
	{
		return to_jstring(env, engine()->snapshot());
	}

	JNIEXPORT jboolean JNICALL Java_org_demonsaw_app_Native_chat(JNIEnv* env, jobject, jstring text)
	{
		return engine()->chat(to_string(env, text)) ? JNI_TRUE : JNI_FALSE;
	}

	JNIEXPORT jboolean JNICALL Java_org_demonsaw_app_Native_join(JNIEnv*, jobject)
	{
		return engine()->join() ? JNI_TRUE : JNI_FALSE;
	}

	JNIEXPORT jboolean JNICALL Java_org_demonsaw_app_Native_browse(JNIEnv*, jobject)
	{
		return engine()->browse() ? JNI_TRUE : JNI_FALSE;
	}

	JNIEXPORT jboolean JNICALL Java_org_demonsaw_app_Native_isRunning(JNIEnv*, jobject)
	{
		return engine()->running() ? JNI_TRUE : JNI_FALSE;
	}
}
