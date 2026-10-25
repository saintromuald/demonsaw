# Build the small subset of Boost that Demonsaw uses directly from a Boost
# source tree. This is required for cross-compilation (Android), where no
# prebuilt Boost binaries exist. Boost 1.69 is the version the code was
# written against.

if(NOT DEMONSAW_BOOST_SOURCE_DIR)
	message(FATAL_ERROR "DEMONSAW_BOOST_SOURCE_DIR must point at a boost_1_69_0 source tree")
endif()

set(_bsrc "${DEMONSAW_BOOST_SOURCE_DIR}/libs")
if(NOT EXISTS "${_bsrc}/filesystem/src/path.cpp")
	message(FATAL_ERROR "Boost source tree looks incomplete: ${DEMONSAW_BOOST_SOURCE_DIR}")
endif()

set(BOOST_INCLUDE_DIR "${DEMONSAW_BOOST_SOURCE_DIR}")

# glibc >= 2.34 defines PTHREAD_STACK_MIN as a function-like macro
# (__sysconf(__SC_THREAD_STACK_MIN_VALUE)), which breaks Boost 1.69's
# `#if PTHREAD_STACK_MIN > 0` in boost/thread/pthread/thread_data.hpp.
# macOS, Bionic and pre-2.34 glibc are unaffected, but host Linux builds
# (and any consumer of the prebuilt-boost path on new glibc) need the fix.
# Instead of mutating the Boost tree we write a patched copy of the header
# into the build directory and shadow it ahead of the real include dir.
# The replacement is semantically equivalent everywhere: when the macro is
# defined it is always positive, and an undefined macro now simply skips
# the clamp, exactly like a defined-but-zero value would have.
set(BOOST_PATCHED_INC "")
set(_thread_data_src "${BOOST_INCLUDE_DIR}/boost/thread/pthread/thread_data.hpp")
if(EXISTS "${_thread_data_src}")
	file(READ "${_thread_data_src}" _thread_data)
	string(FIND "${_thread_data}" "#if PTHREAD_STACK_MIN > 0" _needs_patch)
	if(NOT _needs_patch EQUAL -1)
		string(REPLACE "#if PTHREAD_STACK_MIN > 0" "#if defined(PTHREAD_STACK_MIN)" _thread_data "${_thread_data}")
		set(BOOST_PATCHED_INC "${CMAKE_BINARY_DIR}/boost_patched")
		file(MAKE_DIRECTORY "${BOOST_PATCHED_INC}/boost/thread/pthread")
		file(WRITE "${BOOST_PATCHED_INC}/boost/thread/pthread/thread_data.hpp" "${_thread_data}")
		message(STATUS "Demonsaw: shadowing patched Boost thread_data.hpp (glibc >= 2.34 fix)")
	endif()
endif()

function(demonsaw_boost_lib name)
	add_library(${name} STATIC ${ARGN})
	target_include_directories(${name} SYSTEM PUBLIC "${BOOST_PATCHED_INC}" "${BOOST_INCLUDE_DIR}")
	target_compile_definitions(${name} PUBLIC
		BOOST_ALL_NO_LIB
		BOOST_CONFIG_SUPPRESS_OUTDATED_MESSAGE)
	set_target_properties(${name} PROPERTIES POSITION_INDEPENDENT_CODE ON)
endfunction()

demonsaw_boost_lib(boost_system "${_bsrc}/system/src/error_code.cpp")

demonsaw_boost_lib(boost_filesystem
	"${_bsrc}/filesystem/src/codecvt_error_category.cpp"
	"${_bsrc}/filesystem/src/operations.cpp"
	"${_bsrc}/filesystem/src/path.cpp"
	"${_bsrc}/filesystem/src/path_traits.cpp"
	"${_bsrc}/filesystem/src/portability.cpp"
	"${_bsrc}/filesystem/src/unique_path.cpp"
	"${_bsrc}/filesystem/src/utf8_codecvt_facet.cpp")
target_link_libraries(boost_filesystem PUBLIC boost_system)

demonsaw_boost_lib(boost_atomic "${_bsrc}/atomic/src/lockpool.cpp")

demonsaw_boost_lib(boost_date_time
	"${_bsrc}/date_time/src/gregorian/date_generators.cpp"
	"${_bsrc}/date_time/src/gregorian/greg_month.cpp"
	"${_bsrc}/date_time/src/gregorian/gregorian_types.cpp"
	"${_bsrc}/date_time/src/gregorian/greg_weekday.cpp"
	"${_bsrc}/date_time/src/posix_time/posix_time_types.cpp")

demonsaw_boost_lib(boost_chrono
	"${_bsrc}/chrono/src/chrono.cpp"
	"${_bsrc}/chrono/src/process_cpu_clocks.cpp"
	"${_bsrc}/chrono/src/thread_clock.cpp")
target_link_libraries(boost_chrono PUBLIC boost_system)

demonsaw_boost_lib(boost_thread
	"${_bsrc}/thread/src/future.cpp"
	"${_bsrc}/thread/src/pthread/once.cpp"
	"${_bsrc}/thread/src/pthread/once_atomic.cpp"
	"${_bsrc}/thread/src/pthread/thread.cpp")
target_link_libraries(boost_thread PUBLIC boost_system boost_chrono boost_date_time boost_atomic Threads::Threads)

add_library(demonsaw_boost INTERFACE)
target_include_directories(demonsaw_boost SYSTEM INTERFACE
	"${BOOST_PATCHED_INC}"
	"${BOOST_INCLUDE_DIR}")
target_link_libraries(demonsaw_boost INTERFACE
	boost_filesystem
	boost_thread
	boost_system
	boost_chrono
	boost_date_time
	boost_atomic
	Threads::Threads)
