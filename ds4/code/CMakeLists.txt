cmake_minimum_required(VERSION 3.20)

project(demonsaw
	VERSION 4.2.1
	DESCRIPTION "Demonsaw 4 - encrypted communications platform"
	LANGUAGES C CXX)

# ---------------------------------------------------------------------------
# Options
# ---------------------------------------------------------------------------
option(DEMONSAW_BUILD_APPS "Build the CLI client and router binaries" ON)
option(DEMONSAW_BUILD_JNI  "Build the Android JNI shared library" OFF)
option(DEMONSAW_BUILD_TESTS "Build development test harnesses" OFF)
option(DEMONSAW_WERROR     "Treat warnings as errors" OFF)

set(DEMONSAW_BOOST_ROOT "" CACHE PATH "Root of a Boost 1.69 source/build tree (headers + stage/lib)")
set(DEMONSAW_BOOST_SOURCE_DIR "" CACHE PATH "Boost 1.69 source tree; when set, Boost is compiled from source (needed for Android)")
set(DEMONSAW_BOOST_LIB_DIR "" CACHE PATH "Directory containing the static Boost libraries (default: <root>/stage/lib)")

set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN ON)

if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
	set(CMAKE_BUILD_TYPE Release CACHE STRING "Build type" FORCE)
endif()

# Crypto++ 7.0 relies on type punning that violates strict aliasing;
# GCC 16 turns that into crashes at -O2 (StringSink/SecByteBlock). The
# upstream Crypto++ documentation recommends -fno-strict-aliasing.
add_compile_options(-fno-strict-aliasing)

set(CMAKE_C_FLAGS_RELEASE "-O2 -DNDEBUG")
set(CMAKE_CXX_FLAGS_RELEASE "-O2 -DNDEBUG")

set(DS_CODE "${CMAKE_CURRENT_SOURCE_DIR}")
set(DS_ROOT "${DS_CODE}/..")

# ---------------------------------------------------------------------------
# Boost 1.69 (static)
# ---------------------------------------------------------------------------
find_package(Threads REQUIRED)

if(DEMONSAW_BOOST_SOURCE_DIR)
	include("${CMAKE_CURRENT_SOURCE_DIR}/cmake/BoostSources.cmake")
else()
	if(NOT DEMONSAW_BOOST_ROOT)
		message(FATAL_ERROR "Set -DDEMONSAW_BOOST_ROOT=/path/to/boost_1_69_0 or -DDEMONSAW_BOOST_SOURCE_DIR=...")
	endif()

	set(BOOST_INCLUDE_DIR "${DEMONSAW_BOOST_ROOT}")
	if(NOT EXISTS "${BOOST_INCLUDE_DIR}/boost/version.hpp")
		message(FATAL_ERROR "No boost/version.hpp under ${BOOST_INCLUDE_DIR}")
	endif()

	if(NOT DEMONSAW_BOOST_LIB_DIR)
		set(DEMONSAW_BOOST_LIB_DIR "${DEMONSAW_BOOST_ROOT}/stage/lib")
	endif()

	function(demonsaw_find_boost_lib out name)
		file(GLOB candidates
			"${DEMONSAW_BOOST_LIB_DIR}/libboost_${name}*.a"
			"${DEMONSAW_BOOST_LIB_DIR}/libboost_${name}*.so")
		list(FILTER candidates EXCLUDE REGEX "-gd(\\.|-)")
		list(FILTER candidates EXCLUDE REGEX "\\.so$")
		if(NOT candidates)
			message(FATAL_ERROR "Boost library '${name}' not found in ${DEMONSAW_BOOST_LIB_DIR}")
		endif()
		list(SORT candidates)
		list(GET candidates 0 picked)
		set(${out} "${picked}" PARENT_SCOPE)
	endfunction()

	foreach(_lib system filesystem thread atomic chrono date_time)
		demonsaw_find_boost_lib(DEMONSAW_BOOST_${_lib} ${_lib})
	endforeach()

	add_library(demonsaw_boost INTERFACE)
	target_include_directories(demonsaw_boost SYSTEM INTERFACE "${BOOST_INCLUDE_DIR}")
	target_link_libraries(demonsaw_boost INTERFACE
		${DEMONSAW_BOOST_filesystem}
		${DEMONSAW_BOOST_thread}
		${DEMONSAW_BOOST_system}
		${DEMONSAW_BOOST_chrono}
		${DEMONSAW_BOOST_date_time}
		${DEMONSAW_BOOST_atomic}
		Threads::Threads)
endif()

# ---------------------------------------------------------------------------
# Vendored third-party libraries
# ---------------------------------------------------------------------------
# Crypto++ 7.0
file(GLOB CRYPTOPP_SOURCES CONFIGURE_DEPENDS "${DS_CODE}/0_cryptopp/cryptopp/*.cpp")
list(FILTER CRYPTOPP_SOURCES EXCLUDE REGEX
	"/(adhoc|bench|bench2|datatest|fipsalgt|regtest|test|validat[0-9])\\.cpp$")
add_library(demonsaw_cryptopp STATIC ${CRYPTOPP_SOURCES})
target_include_directories(demonsaw_cryptopp PUBLIC "${DS_CODE}/0_cryptopp")
target_compile_definitions(demonsaw_cryptopp PUBLIC CRYPTOPP_DISABLE_ASM)

if(ANDROID)
	# Crypto++ probes the CPU through the NDK's cpufeatures helper.
	target_sources(demonsaw_cryptopp PRIVATE
		"${ANDROID_NDK}/sources/android/cpufeatures/cpu-features.c")
	target_include_directories(demonsaw_cryptopp PRIVATE
		"${ANDROID_NDK}/sources/android/cpufeatures")
endif()

# cpp-netlib 0.13 (only the four translation units the qmake build used)
add_library(demonsaw_cppnetlib STATIC
	"${DS_CODE}/0_cppnetlib/libs/network/src/client.cpp"
	"${DS_CODE}/0_cppnetlib/libs/network/src/server_request_parsers_impl.cpp"
	"${DS_CODE}/0_cppnetlib/libs/network/src/uri/schemes.cpp"
	"${DS_CODE}/0_cppnetlib/libs/network/src/uri/uri.cpp")
target_include_directories(demonsaw_cppnetlib PUBLIC "${DS_CODE}/0_cppnetlib")
target_link_libraries(demonsaw_cppnetlib PUBLIC demonsaw_boost)

# msgpack-c is header-only for the C++ API used here
add_library(demonsaw_msgpack INTERFACE)
target_include_directories(demonsaw_msgpack SYSTEM INTERFACE "${DS_CODE}/0_msgpack/include")

# ---------------------------------------------------------------------------
# Core libraries (Qt-free)
# ---------------------------------------------------------------------------
file(GLOB_RECURSE CORE_SOURCES CONFIGURE_DEPENDS "${DS_CODE}/4_core/*.cpp")
file(GLOB_RECURSE CORE_C_SOURCES CONFIGURE_DEPENDS "${DS_CODE}/4_core/*.c")
add_library(demonsaw_core STATIC ${CORE_SOURCES} ${CORE_C_SOURCES})
target_include_directories(demonsaw_core PUBLIC
	"${DS_CODE}/4_core"
	"${DS_CODE}/0_cryptopp"
	"${DS_CODE}/0_msgpack/include")
target_link_libraries(demonsaw_core PUBLIC
	demonsaw_cryptopp
	demonsaw_cppnetlib
	demonsaw_msgpack
	demonsaw_boost)
target_compile_definitions(demonsaw_core PUBLIC
	BOOST_CONFIG_SUPPRESS_OUTDATED_MESSAGE
	CRYPTOPP_DISABLE_ASM)

file(GLOB_RECURSE CLIENT_CORE_SOURCES CONFIGURE_DEPENDS "${DS_CODE}/4_core_client/*.cpp")
add_library(demonsaw_client_core STATIC ${CLIENT_CORE_SOURCES})
target_include_directories(demonsaw_client_core PUBLIC
	"${DS_CODE}/4_core_client"
	"${DS_CODE}/4_core")
target_link_libraries(demonsaw_client_core PUBLIC demonsaw_core)

# ---------------------------------------------------------------------------
# Applications
# ---------------------------------------------------------------------------
if(DEMONSAW_BUILD_APPS)
	file(GLOB_RECURSE CLI_SOURCES CONFIGURE_DEPENDS "${DS_CODE}/4_client_cli/*.cpp")
	add_executable(demonsaw_cli ${CLI_SOURCES})
	target_include_directories(demonsaw_cli PRIVATE "${DS_CODE}/4_client_cli")
	target_link_libraries(demonsaw_cli PRIVATE demonsaw_client_core)

	file(GLOB_RECURSE ROUTER_SOURCES CONFIGURE_DEPENDS "${DS_CODE}/4_router_cli/*.cpp")
	add_executable(demonsaw_router ${ROUTER_SOURCES})
	target_include_directories(demonsaw_router PRIVATE "${DS_CODE}/4_router_cli")
	target_link_libraries(demonsaw_router PRIVATE demonsaw_core)
	set_target_properties(demonsaw_router PROPERTIES OUTPUT_NAME demonsaw_router)
endif()

# ---------------------------------------------------------------------------
# Android JNI library
# ---------------------------------------------------------------------------
if(DEMONSAW_BUILD_JNI)
	add_subdirectory("${DS_CODE}/android/jni" "${CMAKE_BINARY_DIR}/jni")
endif()

# ---------------------------------------------------------------------------
# Development test harnesses (host)
# ---------------------------------------------------------------------------
if(DEMONSAW_BUILD_TESTS)
	add_executable(demonsaw_engine_test
		"${DS_CODE}/android/engine/engine_test.cpp"
		"${DS_CODE}/android/engine/engine.cpp"
		"${DS_CODE}/4_client_cli/config/cli_application.cpp")
	target_include_directories(demonsaw_engine_test PRIVATE
		"${DS_CODE}"
		"${DS_CODE}/android"
		"${DS_CODE}/4_client_cli")
	target_link_libraries(demonsaw_engine_test PRIVATE demonsaw_client_core)
endif()

# ---------------------------------------------------------------------------
# Install (host)
# ---------------------------------------------------------------------------
include(GNUInstallDirs)
if(DEMONSAW_BUILD_APPS)
	install(TARGETS demonsaw_cli demonsaw_router RUNTIME DESTINATION "${CMAKE_INSTALL_BINDIR}")
endif()
