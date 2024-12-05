#include <stddef.h>

#include <ch32v00x_usart.h>
#include <ch32v00x_gpio.h>
#include <ch32v00x_rcc.h>

#include "ch32v00x-sys.h"
#include "debug.h"

static uint8_t  p_us = 0;
static uint16_t p_ms = 0;

void delay_init(void)
{
	p_us = SystemCoreClock / 8000000;
	p_ms = (uint16_t)p_us * 1000;
}

void delay_us(uint32_t n)
{
	uint32_t i;

	SysTick->SR &= ~(1 << 0);
	i = (uint32_t)n * p_us;

	SysTick->CMP = i;
	SysTick->CNT = 0;
	SysTick->CTLR |=(1 << 0);

	while((SysTick->SR & (1 << 0)) != (1 << 0));
	SysTick->CTLR &= ~(1 << 0);
}

void delay_ms(uint32_t n)
{
	uint32_t i;

	SysTick->SR &= ~(1 << 0);
	i = (uint32_t)n * p_ms;

	SysTick->CMP = i;
	SysTick->CNT = 0;
	SysTick->CTLR |=(1 << 0);

	while((SysTick->SR & (1 << 0)) != (1 << 0));
	SysTick->CTLR &= ~(1 << 0);
}

void USART_printf_init(uint32_t baudrate)
{
	RCC_APB2PeriphClockCmd(RCC_APB2Periph_GPIOD | RCC_APB2Periph_USART1, ENABLE);

	GPIO_InitTypeDef iocfg = {0};
	iocfg.GPIO_Pin = GPIO_Pin_5;
	iocfg.GPIO_Mode = GPIO_Mode_AF_PP;
	iocfg.GPIO_Speed = GPIO_Speed_50MHz;
	GPIO_Init(GPIOD, &iocfg);

	USART_InitTypeDef uart_cfg;
	uart_cfg.USART_BaudRate = baudrate;
	uart_cfg.USART_WordLength = USART_WordLength_8b;
	uart_cfg.USART_StopBits = USART_StopBits_1;
	uart_cfg.USART_Parity = USART_Parity_No;
	uart_cfg.USART_HardwareFlowControl = USART_HardwareFlowControl_None;
	uart_cfg.USART_Mode = USART_Mode_Tx;

	USART_Init(USART1, &uart_cfg);
	USART_Cmd(USART1, ENABLE);
}

__attribute__((used)) 
int _write(int fd, char *buf, int size)
{
	int i;

	for(i = 0; i < size; i++){
		while(USART_GetFlagStatus(USART1, USART_FLAG_TC) == RESET);
		USART_SendData(USART1, *buf++);
	}

	return size;
}

void *_sbrk(ptrdiff_t incr)
{
	extern char _end[];
	extern char _heap_end[];
	static char *curbrk = _end;

	if ((curbrk + incr < _end) || (curbrk + incr > _heap_end))
	return NULL - 1;

	curbrk += incr;
	return curbrk - incr;
}

void debug_init()
{
	USART_printf_init(DEBUG_BAUD);
	PRINT("SystemClk: %d\n", SystemCoreClock);
}
