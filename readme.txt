Openflower
==========
by abec2304

For Fernflower 0.8.4, by Stiver. Utilizes Javassist for class manipulation.

Openflower is a project similar to MCP, but for Fernflower instead of Minecraft.
It was initially for Fernflower 0.8.6, but eventually switched to Fernflower 0.8.4.

Openflower produces a deobfuscated decompilation of Fernflower.
Several patches are applied to the Fernflower so that it can be correctly decompiled.
Note that the patches are present in the decompiled sourcecode, for convenience.

Openflower does the following:
* patches Fernflower to improve decompilation
* remaps classes, fields and methods to non-obfuscated names
(note: the names are based upon those from the Fernflower 0.8.4 source on Github)
* removes 'bridge' from access flags of some methods
* restores inner class attributes

The following patches are applied:
* print stack traces
* fix for occasional missing casts
* fix for broken switch case sorting
* fix for missing while(true)
* fix for 'this' assignment
* fix for occasional broken instantiation
* patch to StructMethod to avoid decompilation issue
* fix for missing assignment of parent fields in constructor
* fix for occasional excessive casting
* fix for broken decompilation of inner class variables
* partial fix for broken qualified new references
