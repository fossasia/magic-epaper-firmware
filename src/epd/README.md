`drv.h` contain glue code in case reused on another platform or
hardware changes.

`*-drv.c` contain platform-dependent implementation of functions specified in
`drv.h`. To port to another platform, just modify these files.

`uc8253.h` and `uc8253.c` contain minimal code to init the display. Real usages
depend on commands in the display driver's datasheet/manual.