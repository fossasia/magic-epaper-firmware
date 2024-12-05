#include <stdlib.h>

#include "mcu/hal.h"
#include "mcu/debug.h"

#include "epd/uc8253.h"
#include "nfc-tag/st25dv.h"

#define CMD_LUT_LEN 2

// Can't use malloc due to not having enough flat mem. Doing this will give us
// flat mem without wasting time to init:
__attribute__ ((section (".noinit"))) uint8_t msg[256];

const void (*cmd_lut[CMD_LUT_LEN])(uint8_t *ptr, uint16_t len) = {
	uc8253_cmd_params, uc8253_send_blk
};

static void dump_mem(uint8_t *buf, int len)
{
	for (int i=0; i<len; i++) {
		PRINT("%02X ", buf[i]);
	}
	PRINT("\n");
}

static void exe_cmd()
{
	if (! ntag_has_new_msg)
		return;

	ntag_msg_len = st25dv_gather_msg(msg);

	uint8_t cmd = msg[0];
	if (cmd >= CMD_LUT_LEN) {
		PRINT("command is not valid!\n");
		return;
	}

	if (cmd_lut[cmd]) {
		(*cmd_lut[cmd])(msg + 1, ntag_msg_len - 1);

		PRINT("[cmd %02x] ", cmd);
		dump_mem(msg + 1, ntag_msg_len - 1);
	} else {
		PRINT("cmd_lut is missing!\n");
	}
	ntag_has_new_msg = 0;
}

int main(void)
{
	hal_init();
	delay_init();
	debug_init();

	st25_enable_FTM();
	st25dv_dump_allregs();

	uc8253_init();
	uc8253_clear_mem();
	// uc8253_refresh_poll();
	PRINT("init done\n");

	while (1) {
		exe_cmd();
	}
}