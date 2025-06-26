#include <ch32v00x_spi.h>
#include <ch32v00x_gpio.h>
#include <ch32v00x_rcc.h>

#include "uc8253.h"
#include "drv.h"
#include "../mcu/debug.h"

#define EPD_BUSY_PIN    GPIO_Pin_4 // pd4
#define EPD_RST_PIN     GPIO_Pin_3 // pd3
#define EPD_DC_PIN      GPIO_Pin_2 // pd2

#define EPD_CS_PIN      GPIO_Pin_4 // pc4
#define EPD_CLK_PIN     GPIO_Pin_5 // pc5
#define EPD_MOSI_PIN    GPIO_Pin_6 // pc6

void uc8253_switch2cmd()
{
	GPIO_WriteBit(GPIOD, EPD_DC_PIN, 0);
}

void uc8253_switch2data()
{
	GPIO_WriteBit(GPIOD, EPD_DC_PIN, 1);
}

void uc8253_select()
{
	GPIO_WriteBit(GPIOC, EPD_CS_PIN, 0);
}

void uc8253_deselect()
{
	GPIO_WriteBit(GPIOC, EPD_CS_PIN, 1);
}

void uc8253_pull_reset()
{
	GPIO_WriteBit(GPIOD, EPD_RST_PIN, 0);
}

void uc8253_release_reset()
{
	GPIO_WriteBit(GPIOD, EPD_RST_PIN, 1);
}

int uc8253_is_busy()
{
	//LOW: busy, HIGH: idle
	return GPIO_ReadInputDataBit(GPIOD, EPD_BUSY_PIN) == 0;
}

void uc8253_init_hal()
{
	RCC_APB2PeriphClockCmd(RCC_APB2Periph_GPIOC | RCC_APB2Periph_GPIOD, ENABLE);

	GPIO_InitTypeDef iocfg = {0};
	iocfg.GPIO_Speed = GPIO_Speed_10MHz;
	
	// reset, data/command as output
	iocfg.GPIO_Pin = EPD_RST_PIN | EPD_DC_PIN;
	iocfg.GPIO_Mode = GPIO_Mode_Out_PP;
	GPIO_Init(GPIOD, &iocfg);

	// BUSY as input
	iocfg.GPIO_Pin = EPD_BUSY_PIN;
	iocfg.GPIO_Mode = GPIO_Mode_IPD;
	GPIO_Init(GPIOD, &iocfg);

	// CS as output
	iocfg.GPIO_Pin = EPD_CS_PIN;
	iocfg.GPIO_Mode = GPIO_Mode_Out_PP;
	GPIO_Init(GPIOC, &iocfg);
	GPIO_WriteBit(GPIOC, EPD_CS_PIN, 1); // Unselect

	// SCK | MOSI
	iocfg.GPIO_Pin = EPD_CLK_PIN | EPD_MOSI_PIN; 
	iocfg.GPIO_Mode = GPIO_Mode_AF_PP;
	// iocfg.GPIO_Mode = GPIO_Mode_Out_PP;
	GPIO_Init(GPIOC, &iocfg);
}

#ifdef USE_SPI_BITBANG
/**
 * handy bit banging if the mcu doesn't have a spi peripheral
 */
void uc8253_write(uint8_t byte)
{
	for (int i=0; i<8; i++) {
		GPIO_WriteBit(GPIOC, EPD_CLK_PIN, 0);
		GPIO_WriteBit(GPIOC, EPD_MOSI_PIN, ((byte << i) & 0x80) == 0x80);
		GPIO_WriteBit(GPIOC, EPD_CLK_PIN, 1);
	}
	GPIO_WriteBit(GPIOC, EPD_CLK_PIN, 0);
}

#else
void uc8253_write(uint8_t byte)
{
	SPI_I2S_SendData(SPI1, byte);
	while (! SPI_I2S_GetFlagStatus(SPI1, SPI_I2S_FLAG_TXE));
}
#endif

// polling
void uc8253_write_blk(uint8_t *buf, int len)
{
	while (len--) {
		uc8253_write(*buf);
		buf++;
	}
}