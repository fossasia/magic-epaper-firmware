#include <ch32v00x_i2c.h>
#include <ch32v00x_exti.h>

#include "../mcu/debug.h"
#include "st25dv.h"
#include "drv.h"

volatile int ntag_has_new_msg, ntag_msg_len;

void st25dv_reads(uint8_t sel, uint16_t addr, uint8_t *val, int size)
{
	__disable_irq();
	// START 1
	while(I2C_GetFlagStatus(I2C1, I2C_FLAG_BUSY) != RESET);
	I2C_GenerateSTART(I2C1, ENABLE);

	while(!I2C_CheckEvent(I2C1, I2C_EVENT_MASTER_MODE_SELECT));
	I2C_Send7bitAddress(I2C1, sel, I2C_Direction_Transmitter);

	while(!I2C_CheckEvent(I2C1, I2C_EVENT_MASTER_TRANSMITTER_MODE_SELECTED));

	I2C_SendData(I2C1, (u8)(addr >> 8));
	while(!I2C_CheckEvent(I2C1, I2C_EVENT_MASTER_BYTE_TRANSMITTED));
	I2C_SendData(I2C1, (u8)(addr & 0xFF));
	while(!I2C_CheckEvent(I2C1, I2C_EVENT_MASTER_BYTE_TRANSMITTED));

	// START 2
	I2C_GenerateSTART(I2C1, ENABLE);

	while(!I2C_CheckEvent(I2C1, I2C_EVENT_MASTER_MODE_SELECT));
	I2C_Send7bitAddress(I2C1, sel, I2C_Direction_Receiver);

	while(!I2C_CheckEvent(I2C1, I2C_EVENT_MASTER_RECEIVER_MODE_SELECTED));
	// while(I2C_GetFlagStatus(I2C1, I2C_FLAG_RXNE) != RESET)
		// I2C_AcknowledgeConfig(I2C1, DISABLE);

	for (int i=0; i<size; i++) {
		// while(I2C_GetFlagStatus(I2C1, I2C_FLAG_RXNE) == RESET);
		while(!I2C_CheckEvent(I2C1, I2C_EVENT_MASTER_BYTE_RECEIVED));
		val[i] = I2C_ReceiveData(I2C1);
	}
	I2C_GenerateSTOP(I2C1, ENABLE);
	__enable_irq();
}

void st25dv_writes(uint8_t sel, uint16_t addr, uint8_t *val, int size)
{
	// START 1
	while(I2C_GetFlagStatus(I2C1, I2C_FLAG_BUSY) != RESET);
	I2C_GenerateSTART(I2C1, ENABLE);

	while(!I2C_CheckEvent(I2C1, I2C_EVENT_MASTER_MODE_SELECT));
	I2C_Send7bitAddress(I2C1, sel, I2C_Direction_Transmitter);

	while(!I2C_CheckEvent(I2C1, I2C_EVENT_MASTER_TRANSMITTER_MODE_SELECTED));

	I2C_SendData(I2C1, (u8)(addr >> 8));
	while(!I2C_CheckEvent(I2C1, I2C_EVENT_MASTER_BYTE_TRANSMITTED));
	I2C_SendData(I2C1, (u8)(addr & 0xFF));
	while(!I2C_CheckEvent(I2C1, I2C_EVENT_MASTER_BYTE_TRANSMITTED));

	for (int i=0; i<size; i++) {
		while(I2C_GetFlagStatus(I2C1, I2C_FLAG_TXE) == RESET);
		I2C_SendData(I2C1, val[i]);
		while(!I2C_CheckEvent(I2C1, I2C_EVENT_MASTER_BYTE_TRANSMITTED));
	}
	I2C_GenerateSTOP(I2C1, ENABLE);
}

void EXTI7_0_IRQHandler(void) __attribute__((interrupt("WCH-Interrupt-fast")));
void EXTI7_0_IRQHandler(void)
{
	__disable_irq();
	if(EXTI_GetITStatus(EXTI_Line3))
	{
		printf("EXTI\n");
		if (st25dv_has_rf_put_msg()) {
			ntag_has_new_msg = 1;
		}
		EXTI_ClearITPendingBit(EXTI_Line3);
	}
	__enable_irq();
}