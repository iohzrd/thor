# Sets the minimum version of CMake required to build your native library.
# This ensures that a certain set of CMake features is available to
# your build.

cmake_minimum_required(VERSION 3.10.2)

include_directories(../../../include)

# Specifies a library name, specifies whether the library is STATIC or
# SHARED, and provides relative paths to the source code. You can
# define multiple libraries by adding multiple add_library() commands,
# and CMake builds them for you. When you build your app, Gradle
# automatically packages shared libraries with your APK.


add_library( # Specifies the name of the library.
             netty_quiche

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
        netty_jni_util.c
        netty_quic_boringssl.c
        netty_quic_quiche.c)


# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in the
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
        netty_quiche

        # Links the target library to the log library
        # included in the NDK.
        # ${log-lib}


        # Specifies the name of the NDK library that
        # you want CMake to locate.
        quiche
        log
        -landroid
        OpenSLES
        z
        c
        GLESv2
        EGL
        dl
        )