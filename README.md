# README #
![cover_compressed.PNG](https://bitbucket.org/repo/qR4Kpr/images/312771496-cover_compressed.PNG)

*(Actual engine footage, but somewhat old, current implementations differs a lot)*

### My Graphics/Game Engine ###

This is a spare time project. I started it when I implemented the project for my master's degree in computer graphics
back in 2013 or so. Its initial purpose was to implement a sandbox where I can quickly iterate and experiment
with implementing different kinds of realtime global illumination.

Since my graduation, I use this project and its quite large amount of code to try out different aspects of
software development and how they work in real-world codebases.
For example I wanted to
* try typing the existing OpenGL api to find out how it would work out and whether Java or Kotlin is strong enough for that.
* refactor an awful lot of code, so that I can find out how to best approach applying heavy changes to large projects in practice.
* use different dependency injection libraries, to find out how they affect code and compare to vanilla di
* practice how to retroactively add a (somewhat) clean api when there was no need for it earlier
* experiment with different data oriented approaches in general and entity component frameworks in particular
* have a large project that mixed Java and Kotlin side by side to find out how it works in practice
* migrate a large codebase from Java to Kotlin in order to find potential obstacles and see whether Kotlin makes a big difference compared to Java
* explore how to best use apis and libraries that are meant to be used with native memory from the JVM

Over the years, different technologies found their way into this project, a non-exhaustive list 

* OpenGL 4.3, 4.5, 4.6
* LWJGL 2, LWJGL 3
* Java 8, Java 17
* Kotlin 1.6, Kotlin 1.9
* JBullet
* Swing with WebLookAndFeel
* Dear ImGui
* Koin, Koin annotations

### Graphics related features that I implemented over time ###

(Some of them may not be working anymore)

* Hierarchical transformations / parenting
* OBJ Loading with Materials
* OpenGL with VBO and VAO, very neat, fast, no index bufffers since they didn't give me a speedup
* Deferred Rendering with Light Accumulation Buffer, One directional light, endless pointlights, arealights, tubelights
* clustered forward rendering (removed)
* BVH accelerated forward rendering for pointlights 
* Extensive per-object-materials with diffusemaps, heightmaps, normalmaps, reflectionmaps, environment maps, specular attributes, reflectiveness attributes for reflections, etc.
* Physically based rendering
* Parallax corrected cubemaps
* A fully dynamic global illumination algorithm developed by myself as a master thesis
* per-material vertex and fragment shaders if you want them
* Parallax mapping
* Instanced rendering for massive object counts
* Variance shadow mapping for directional light
* PCF shadow mapping configurable
* HDR rendering with tone mapping
* Gamma correct rendering
* HDR auto exposure
* Screen space reflections, reflection mapping, per object environment maps
* SS Ambient Occlusion, SS directional occlusion
* Compressed textures
* Everything is serializable for fast streaming and loading (removed)
* Loose octree culling
* Multithreaded rendering architecture
* In-Engine editor written in vanilla Swing (removed)
* In-Engine editor written in Swing with weblookandfeel (removed)
* In-Engine editor written based on Ribbon framework (removed)
* In-Engine editor written based on Dear ImGui
* hardware based tesselation
* voxel cone tracing with realtime voxelization for global illumination
* cascaded voxel cone tracing (removed)
* ocean rendering
* two-phase occlusion culling

There are some videos on my YouTube channel on https://www.youtube.com/@hannomalie/videos showcasing some of the mentioned features,
(click on the images):

[![IMAGE ALT TEXT HERE](https://img.youtube.com/vi/vfwO2LeOqyA/0.jpg)](https://www.youtube.com/watch?v=vfwO2LeOqyA)

[![IMAGE ALT TEXT HERE](https://img.youtube.com/vi/AwSfeo0xui4/0.jpg)](https://www.youtube.com/watch?v=AwSfeo0xui4)

[![IMAGE ALT TEXT HERE](https://img.youtube.com/vi/_omK2N5-M4g/0.jpg)](https://www.youtube.com/watch?v=_omK2N5-M4g)

[![IMAGE ALT TEXT HERE](https://img.youtube.com/vi/9UGc6gn6sXA/0.jpg)](https://www.youtube.com/watch?v=9UGc6gn6sXA)

[![IMAGE ALT TEXT HERE](https://img.youtube.com/vi/383EKvaU2vE/0.jpg)](https://www.youtube.com/watch?v=383EKvaU2vE)

[![IMAGE ALT TEXT HERE](https://img.youtube.com/vi/RSF-FyNLbbY/0.jpg)](https://www.youtube.com/watch?v=RSF-FyNLbbY)
