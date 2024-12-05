#include "debug.h"

void NMI_Handler(void) __attribute__((interrupt("WCH-Interrupt-fast")));
void NMI_Handler(void)
{
	PRINT("NMI exception\n");
}

void HardFault_Handler(void) __attribute__((interrupt("WCH-Interrupt-fast")));
void HardFault_Handler(void)
{
	PRINT("hard fault\n");
	while (1);
}
