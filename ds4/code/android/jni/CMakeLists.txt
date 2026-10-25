add_library(demonsaw SHARED
	"${DS_CODE}/android/jni/demonsaw_jni.cpp"
	"${DS_CODE}/android/engine/engine.cpp"
	"${DS_CODE}/4_client_cli/config/cli_application.cpp")

target_include_directories(demonsaw PRIVATE
	"${DS_CODE}"
	"${DS_CODE}/android"
	"${DS_CODE}/4_client_cli")

target_link_libraries(demonsaw PRIVATE demonsaw_client_core)

target_compile_options(demonsaw PRIVATE -fvisibility=hidden -fexceptions -frtti)

# Android 15 requires 16 KB page-size compatible native libraries.
if(ANDROID)
	target_link_options(demonsaw PRIVATE "-Wl,-z,max-page-size=16384")
endif()

set_target_properties(demonsaw PROPERTIES OUTPUT_NAME demonsaw)
