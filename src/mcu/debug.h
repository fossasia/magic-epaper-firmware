#ifndef __DEBUG_H__
#define __DEBUG_H__

#include <stdio.h>

#ifdef  DEBUG
#define PRINT(X...) printf(X)
#else
#define PRINT(X...)
#endif

void delay_init(void);
void delay_us(uint32_t n);
void delay_ms(uint32_t n);
void USART_printf_init(uint32_t baudrate);

void debug_init();

#endif /* __DEBUG_H__ */
