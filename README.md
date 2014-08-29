# README #
![hpengine_water_tinted.jpg](https://bitbucket.org/repo/qR4Kpr/images/2117027051-hpengine_water_tinted.jpg)
### My Graphics/Game Engine ###

This engine is my hobby project - I wanted to learn more about graphics programming, OpenGL and have a motivating project again :) It's written from scratch, based on LWJGLs OpenGL bindings. 95% is developed by myself, some snippets like the dds loader came from the interwebs. Thanks to everybody who silently contributed!

* OpenGL 4.3
* LWJGL
* Java 1.8
* JBullet
* Swing with WebLookAndFeel

### Features ###

* OBJ Loading with Materials
* OpenGL with VBO and VAO, very neat, fast, no index bufffers since they didn't give me a speedup
* Deferred Rendering with Light Accumulation Buffer, One directional light, endless pointlights
* Extensive per-object-materials with diffusemaps, heightmaps, normalmaps, reflectionmaps, environment maps, specular attributes, reflectiveness attributes for reflections, etc.
* per-material vertex and fragment shaders if you want them
* Parallax mapping
* Screen Space local reflections
* Variance shadowmapping for directional light
* HDR rendering with tone mapping
* Screen space reflections, reflectionmapping, per object environment maps
* SS Ambient Occlusion, (SS directional occlusion started)
* Compressed textures
* Everything is serializable for fast streaming
* Loose octree culling
* JBullet integration started
* Aaaaaaand - my wonderful GUI-Editor with scripting interface, texture lib, scene tree, material editor...everything realtime and multithreaded and stuff.


### Who do I talk to? ###

* Just me