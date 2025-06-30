#include "uc8253.h"
#include "drv.h"
#include "../mcu/debug.h"

#define PANEL_SETTING                               0x00
#define POWER_SETTING                               0x01
#define POWER_OFF                                   0x02
#define POWER_OFF_SEQUENCE_SETTING                  0x03
#define POWER_ON                                    0x04
#define POWER_ON_MEASURE                            0x05
#define BOOSTER_SOFT_START                          0x06
#define DEEP_SLEEP                                  0x07
#define DATA_START_TRANSMISSION_1                   0x10 // Black-white
#define DATA_STOP                                   0x11
#define DISPLAY_REFRESH                             0x12
#define DATA_START_TRANSMISSION_2                   0x13 // RED
#define VCOM_LUT                                    0x20
#define W2W_LUT                                     0x21
#define B2W_LUT                                     0x22
#define W2B_LUT                                     0x23
#define B2B_LUT                                     0x24
#define BORDER_LUT                                  0x25
#define PLL_CONTROL                                 0x30
#define TEMPERATURE_SENSOR_CALIBRATION              0x40
#define TEMPERATURE_SENSOR_SELECTION                0x41
#define TEMPERATURE_SENSOR_WRITE                    0x42
#define TEMPERATURE_SENSOR_READ                     0x43
#define VCOM_AND_DATA_INTERVAL_SETTING              0x50
#define LOW_POWER_DETECTION                         0x51
#define TCON_SETTING                                0x60
#define RESOLUTION_SETTING                          0x61
#define GSST                                        0x65
#define GET_STATUS                                  0x71
#define AUTO_MEASURE_VCOM                           0x80
#define READ_VCOM_VALUE                             0x81
#define VCM_DC_SETTING                              0x82
#define PARTIAL_WINDOW                              0x90
#define PARTIAL_IN                                  0x91
#define PARTIAL_OUT                                 0x92
#define PROGRAM_MODE                                0xA0
#define ACTIVE_PROGRAM                              0xA1
#define READ_OTP_DATA                               0xA2
#define POWER_SAVING                                0xE3

#define epd_WIDTH       240
#define epd_HEIGHT      416

static void reset(void)
{
	for (int i=0; i<3; i++) {
		uc8253_pull_reset();
		delay_ms(10);
		uc8253_release_reset();
		delay_ms(10);
	}
}

void uc8253_send(uint8_t byte)
{
	uc8253_switch2data();

	uc8253_select();
	uc8253_write(byte);
	uc8253_deselect();
}

void uc8253_send_blk(uint8_t *bytes, uint16_t len)
{
	uc8253_switch2data();

	uc8253_select();
	uc8253_write_blk(bytes, len);
	uc8253_deselect();
}

void uc8253_cmd(uint8_t cmd)
{
	PRINT(__func__);
	PRINT("\n");
	uc8253_switch2cmd();

	uc8253_select();
	uc8253_write(cmd);
	uc8253_deselect();
}

void uc8253_cmd_params(uint8_t *cmd_and_params, uint16_t len)
{
	uint8_t cmd = cmd_and_params[0];
	uc8253_cmd(cmd);
	uc8253_send_blk(cmd_and_params + 1, len - 1);
}

// wating for BUSY pin to release
static void wait(void)
{
	PRINT(__func__);
	PRINT("\n");
	if (uc8253_is_busy())
		PRINT("epd> wating.. ");
	else return;

	// uc8253_cmd(GET_STATUS);
	while(uc8253_is_busy()) {
		// uc8253_cmd(GET_STATUS);
		delay_ms(100);
	}
	printf("released.\n");
}

/**
 * @brief 		Set cursor to x, y
 * 
 * @param		x8 Horizontal position, unit of 8 pixels
 * @param		y Vertical position
 */
void uc8253_gotoxy(uint16_t x8, uint16_t y)
{
	if (x8 > epd_WIDTH && y > epd_HEIGHT) return;

	uc8253_cmd(GSST);
	uc8253_send(x8 << 3);
	uc8253_send((y >> 8) & 0x01);
	uc8253_send(y & 0xFF);
}

/**
 * @brief 		Set cursor to x, y
 * 
 * @param		w8 width, unit of 8 pixels
 * @param		y height
 */
void uc8253_set_resolution(uint16_t w, uint16_t h)
{
	if (w > epd_WIDTH && h > epd_HEIGHT) return;

	uc8253_cmd(RESOLUTION_SETTING);
	uc8253_send(w << 3);
	uc8253_send((h >> 8) & 0x03);
	uc8253_send(h & 0xFF);
}

void uc8253_clear_mem(void)
{
	uint16_t w, h, i, j;
	w = (epd_WIDTH % 8 == 0)? (epd_WIDTH / 8 ): (epd_WIDTH / 8 + 1);
	h = epd_HEIGHT;

	uc8253_cmd(DATA_START_TRANSMISSION_1);
	for (j = 0; j < h; j++) {
		for (i = 0; i < w; i++) {
			uc8253_send(0xFF);
		}
	}

	uc8253_cmd(DATA_START_TRANSMISSION_2);
	for (j = 0; j < h; j++) {
		for (i = 0; i < w; i++) {
			uc8253_send(0xFF);
		}
	}

	uc8253_gotoxy(0, 0);
}

void uc8253_refresh_poll()
{
	PRINT(__func__);
	PRINT("\n");
	uc8253_cmd(DISPLAY_REFRESH);
	wait();
}

void uc8253_init() {
	uc8253_init_hal();

	reset();
}