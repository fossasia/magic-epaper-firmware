#include <ch32v00x_gpio.h>
#include <ch32v00x_rcc.h>
#include <ch32v00x_exti.h>
#include <ch32v00x_misc.h>
#include <ch32v00x_i2c.h>
#include <ch32v00x_spi.h>

static void i2c_init()
{
	RCC_APB2PeriphClockCmd(RCC_APB2Periph_GPIOC | RCC_APB2Periph_AFIO, ENABLE);
	RCC_APB1PeriphClockCmd(RCC_APB1Periph_I2C1, ENABLE);

	GPIO_InitTypeDef pins_init={0};
	pins_init.GPIO_Pin = GPIO_Pin_1 | GPIO_Pin_2;
	pins_init.GPIO_Mode = GPIO_Mode_AF_OD;
	pins_init.GPIO_Speed = GPIO_Speed_2MHz;
	GPIO_Init(GPIOC, &pins_init);

	I2C_InitTypeDef i2ccfg={0};
	i2ccfg.I2C_ClockSpeed = 400000;
	i2ccfg.I2C_Mode = I2C_Mode_I2C;
	i2ccfg.I2C_DutyCycle = I2C_DutyCycle_16_9;
	i2ccfg.I2C_Ack = I2C_Ack_Enable;
	i2ccfg.I2C_AcknowledgedAddress = I2C_AcknowledgedAddress_7bit;
	I2C_Init(I2C1, &i2ccfg);

	I2C_Cmd(I2C1, ENABLE);

	I2C_AcknowledgeConfig(I2C1, ENABLE);
}

static void spi_init()
{
	RCC_APB2PeriphClockCmd(RCC_APB2Periph_SPI1, ENABLE);
	SPI_InitTypeDef spi={0};
	spi.SPI_Direction = SPI_Direction_1Line_Tx;
	spi.SPI_Mode = SPI_Mode_Master;

	spi.SPI_DataSize = SPI_DataSize_8b;
	spi.SPI_CPOL = SPI_CPOL_Low;
	spi.SPI_CPHA = SPI_CPHA_1Edge;
	spi.SPI_NSS = SPI_NSS_Soft;
	spi.SPI_BaudRatePrescaler = SPI_BaudRatePrescaler_2;
	spi.SPI_FirstBit = SPI_FirstBit_MSB;
	SPI_Init(SPI1, &spi);

	SPI_Cmd(SPI1, ENABLE);
}

static void setup_GPO()
{
	RCC_APB2PeriphClockCmd(RCC_APB2Periph_AFIO | RCC_APB2Periph_GPIOC, ENABLE);

	GPIO_InitTypeDef p = {0};
	p.GPIO_Pin = GPIO_Pin_3;
	p.GPIO_Speed = GPIO_Speed_2MHz;
	p.GPIO_Mode = GPIO_Mode_IPU;
	GPIO_Init(GPIOC, &p);

	GPIO_EXTILineConfig(GPIO_PortSourceGPIOC, 3);
	EXTI_InitTypeDef ei = {0};
	ei.EXTI_Line = EXTI_Line3;
	ei.EXTI_Mode = EXTI_Mode_Interrupt;
	ei.EXTI_Trigger = EXTI_Trigger_Falling;
	ei.EXTI_LineCmd = ENABLE;
	EXTI_Init(&ei);

	NVIC_InitTypeDef nvic = {0};
	nvic.NVIC_IRQChannel = EXTI7_0_IRQn;
	nvic.NVIC_IRQChannelPreemptionPriority = NVIC_PriorityGroup_3;
	nvic.NVIC_IRQChannelSubPriority = 4;
	nvic.NVIC_IRQChannelCmd = ENABLE;
	NVIC_Init(&nvic);	
}

void hal_init()
{
	NVIC_PriorityGroupConfig(NVIC_PriorityGroup_4);

	i2c_init();

	spi_init();

	setup_GPO();
}