`drv.h` contain glue code in case reused on another platform or
hardware changes.

`*-drv.c` contain platform-dependent implementation of functions specified in
`drv.h`. To port to another platform, just modify these files.

`st25dv.h` and `st25dv.c` contain several functions to dump st25dv's registers,
open/close/check security session, check/read message from rf interface.