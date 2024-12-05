/********************************** (C) COPYRIGHT *******************************
 * File Name          : ch32v00x_spi.c
 * Author             : WCH
 * Version            : V1.0.0
 * Date               : 2022/08/08
 * Description        : This file provides all the SPI firmware functions.
 *********************************************************************************
 * Copyright (c) 2021 Nanjing Qinheng Microelectronics Co., Ltd.
 * Attention: This software (modified or not) and binary are used for 
 * microcontroller manufactured by Nanjing Qinheng Microelectronics.
 *******************************************************************************/
#include <ch32v00x_rcc.h>
#include <ch32v00x_spi.h>

/* SPI SPE mask */
#define CTLR1_SPE_Set         ((uint16_t)0x0040)
#define CTLR1_SPE_Reset       ((uint16_t)0xFFBF)

/* SPI CRCNext mask */
#define CTLR1_CRCNext_Set     ((uint16_t)0x1000)

/* SPI CRCEN mask */
#define CTLR1_CRCEN_Set       ((uint16_t)0x2000)
#define CTLR1_CRCEN_Reset     ((uint16_t)0xDFFF)

/* SPI SSOE mask */
#define CTLR2_SSOE_Set        ((uint16_t)0x0004)
#define CTLR2_SSOE_Reset      ((uint16_t)0xFFFB)

/* SPI registers Masks */
#define CTLR1_CLEAR_Mask      ((uint16_t)0x3040)
#define I2SCFGR_CLEAR_Mask    ((uint16_t)0xF040)


/*********************************************************************
 * @fn      SPI_I2S_DeInit
 *
 * @brief   Deinitializes the SPIx peripheral registers to their default
 *        reset values.
 * @param   SPIx - where x can be 1 to select the SPI peripheral.
 *
 * @return  none
 */
void SPI_I2S_DeInit(SPI_TypeDef *SPIx)
{
    if(SPIx == SPI1)
    {
        RCC_APB2PeriphResetCmd(RCC_APB2Periph_SPI1, ENABLE);
        RCC_APB2PeriphResetCmd(RCC_APB2Periph_SPI1, DISABLE);
    }
}

/*********************************************************************
 * @fn      SPI_Init
 *
 * @brief   Initializes the SPIx peripheral according to the specified
 *        parameters in the SPI_InitStruct.
 *
 * @param   SPIx - where x can be 1 to select the SPI peripheral.
 *          SPI_InitStruct - pointer to a SPI_InitTypeDef structure that
 *        contains the configuration information for the specified SPI peripheral.
 *
 * @return  none
 */
void SPI_Init(SPI_TypeDef *SPIx, SPI_InitTypeDef *SPI_InitStruct)
{
    uint16_t tmpreg = 0;

    tmpreg = SPIx->CTLR1;
    tmpreg &= CTLR1_CLEAR_Mask;
    tmpreg |= (uint16_t)((uint32_t)SPI_InitStruct->SPI_Direction | SPI_InitStruct->SPI_Mode |
                         SPI_InitStruct->SPI_DataSize | SPI_InitStruct->SPI_CPOL |
                         SPI_InitStruct->SPI_CPHA | SPI_InitStruct->SPI_NSS |
                         SPI_InitStruct->SPI_BaudRatePrescaler | SPI_InitStruct->SPI_FirstBit);

    SPIx->CTLR1 = tmpreg;
    SPIx->CRCR = SPI_InitStruct->SPI_CRCPolynomial;
}

/*********************************************************************
 * @fn      SPI_StructInit
 *
 * @brief   Fills each SPI_InitStruct member with its default value.
 *
 * @param   SPI_InitStruct - pointer to a SPI_InitTypeDef structure which
 *        will be initialized.
 *
 * @return  none
 */
void SPI_StructInit(SPI_InitTypeDef *SPI_InitStruct)
{
    SPI_InitStruct->SPI_Direction = SPI_Direction_2Lines_FullDuplex;
    SPI_InitStruct->SPI_Mode = SPI_Mode_Slave;
    SPI_InitStruct->SPI_DataSize = SPI_DataSize_8b;
    SPI_InitStruct->SPI_CPOL = SPI_CPOL_Low;
    SPI_InitStruct->SPI_CPHA = SPI_CPHA_1Edge;
    SPI_InitStruct->SPI_BaudRatePrescaler = SPI_BaudRatePrescaler_2;
    SPI_InitStruct->SPI_FirstBit = SPI_FirstBit_MSB;
    SPI_InitStruct->SPI_CRCPolynomial = 7;
}

/*********************************************************************
 * @fn      SPI_Cmd
 *
 * @brief   Enables or disables the specified SPI peripheral.
 *
 * @param   SPIx - where x can be 1 to select the SPI peripheral.
 *          NewState - ENABLE or DISABLE.
 *
 * @return  none
 */
void SPI_Cmd(SPI_TypeDef *SPIx, FunctionalState NewState)
{
    if(NewState != DISABLE)
    {
        SPIx->CTLR1 |= CTLR1_SPE_Set;
    }
    else
    {
        SPIx->CTLR1 &= CTLR1_SPE_Reset;
    }
}

/*********************************************************************
 * @fn      SPI_I2S_ITConfig
 *
 * @brief   Enables or disables the specified SPI interrupts.
 *
 * @param   SPIx - where x can be
 *            - 1 in SPI mode.
 *          SPI_I2S_IT - specifies the SPI interrupt source to be
 *        enabled or disabled.
 *            SPI_I2S_IT_TXE - Tx buffer empty interrupt mask.
 *            SPI_I2S_IT_RXNE - Rx buffer not empty interrupt mask.
 *            SPI_I2S_IT_ERR - Error interrupt mask.
 *          NewState: ENABLE or DISABLE.
 * @return  none
 */
void SPI_I2S_ITConfig(SPI_TypeDef *SPIx, uint8_t SPI_I2S_IT, FunctionalState NewState)
{
    uint16_t itpos = 0, itmask = 0;

    itpos = SPI_I2S_IT >> 4;
    itmask = (uint16_t)1 << (uint16_t)itpos;

    if(NewState != DISABLE)
    {
        SPIx->CTLR2 |= itmask;
    }
    else
    {
        SPIx->CTLR2 &= (uint16_t)~itmask;
    }
}

/*********************************************************************
 * @fn      SPI_I2S_DMACmd
 *
 * @brief   Enables or disables the SPIx DMA interface.
 *
 * @param   SPIx - where x can be
 *            - 1 in SPI mode.
 *          SPI_I2S_DMAReq - specifies the SPI DMA transfer request to
 *        be enabled or disabled.
 *            SPI_I2S_DMAReq_Tx - Tx buffer DMA transfer request.
 *            SPI_I2S_DMAReq_Rx - Rx buffer DMA transfer request.
 *          NewState - ENABLE or DISABLE.
 *
 * @return  none
 */
void SPI_I2S_DMACmd(SPI_TypeDef *SPIx, uint16_t SPI_I2S_DMAReq, FunctionalState NewState)
{
    if(NewState != DISABLE)
    {
        SPIx->CTLR2 |= SPI_I2S_DMAReq;
    }
    else
    {
        SPIx->CTLR2 &= (uint16_t)~SPI_I2S_DMAReq;
    }
}

/*********************************************************************
 * @fn      SPI_I2S_SendData
 *
 * @brief   Transmits a Data through the SPIx peripheral.
 *
 * @param   SPIx - where x can be
 *            - 1 in SPI mode.
 *          Data - Data to be transmitted.
 *
 * @return  none
 */
void SPI_I2S_SendData(SPI_TypeDef *SPIx, uint16_t Data)
{
    SPIx->DATAR = Data;
}

/*********************************************************************
 * @fn      SPI_I2S_ReceiveData
 *
 * @brief   Returns the most recent received data by the SPIx peripheral.
 *
 * @param   SPIx - where x can be
 *            - 1 in SPI mode.
 *          Data - Data to be transmitted.
 *
 * @return  SPIx->DATAR - The value of the received data.
 */
uint16_t SPI_I2S_ReceiveData(SPI_TypeDef *SPIx)
{
    return SPIx->DATAR;
}

/*********************************************************************
 * @fn      SPI_NSSInternalSoftwareConfig
 *
 * @brief   Configures internally by software the NSS pin for the selected SPI.
 *
 * @param   SPIx - where x can be 1 to select the SPI peripheral.
 *          SPI_NSSInternalSoft -
 *            SPI_NSSInternalSoft_Set - Set NSS pin internally.
 *            SPI_NSSInternalSoft_Reset - Reset NSS pin internally.
 *
 * @return  none
 */
void SPI_NSSInternalSoftwareConfig(SPI_TypeDef *SPIx, uint16_t SPI_NSSInternalSoft)
{
    if(SPI_NSSInternalSoft != SPI_NSSInternalSoft_Reset)
    {
        SPIx->CTLR1 |= SPI_NSSInternalSoft_Set;
    }
    else
    {
        SPIx->CTLR1 &= SPI_NSSInternalSoft_Reset;
    }
}

/*********************************************************************
 * @fn      SPI_SSOutputCmd
 *
 * @brief   Enables or disables the SS output for the selected SPI.
 *
 * @param   SPIx - where x can be 1 to select the SPI peripheral.
 *          NewState - new state of the SPIx SS output.
 *
 * @return  none
 */
void SPI_SSOutputCmd(SPI_TypeDef *SPIx, FunctionalState NewState)
{
    if(NewState != DISABLE)
    {
        SPIx->CTLR2 |= CTLR2_SSOE_Set;
    }
    else
    {
        SPIx->CTLR2 &= CTLR2_SSOE_Reset;
    }
}

/*********************************************************************
 * @fn      SPI_DataSizeConfig
 *
 * @brief   Configures the data size for the selected SPI.
 *
 * @param   SPIx - where x can be 1 to select the SPI peripheral.
 *          SPI_DataSize - specifies the SPI data size.
 *            SPI_DataSize_16b - Set data frame format to 16bit.
 *            SPI_DataSize_8b - Set data frame format to 8bit.
 *
 * @return  none
 */
void SPI_DataSizeConfig(SPI_TypeDef *SPIx, uint16_t SPI_DataSize)
{
    SPIx->CTLR1 &= (uint16_t)~SPI_DataSize_16b;
    SPIx->CTLR1 |= SPI_DataSize;
}

/*********************************************************************
 * @fn      SPI_TransmitCRC
 *
 * @brief   Transmit the SPIx CRC value.
 *
 * @param   SPIx - where x can be 1 to select the SPI peripheral.
 *
 * @return  none
 */
void SPI_TransmitCRC(SPI_TypeDef *SPIx)
{
    SPIx->CTLR1 |= CTLR1_CRCNext_Set;
}

/*********************************************************************
 * @fn      SPI_CalculateCRC
 *
 * @brief   Enables or disables the CRC value calculation of the transferred bytes.
 *
 * @param   SPIx - where x can be 1 to select the SPI peripheral.
 *          NewState - new state of the SPIx CRC value calculation.
 *
 * @return  none
 */
void SPI_CalculateCRC(SPI_TypeDef *SPIx, FunctionalState NewState)
{
    if(NewState != DISABLE)
    {
        SPIx->CTLR1 |= CTLR1_CRCEN_Set;
    }
    else
    {
        SPIx->CTLR1 &= CTLR1_CRCEN_Reset;
    }
}

/*********************************************************************
 * @fn      SPI_GetCRC
 *
 * @brief   Returns the transmit or the receive CRC register value for the specified SPI.
 *
 * @param   SPIx - where x can be 1 to select the SPI peripheral.
 *          SPI_CRC - specifies the CRC register to be read.
 *            SPI_CRC_Tx - Selects Tx CRC register.
 *            SPI_CRC_Rx - Selects Rx CRC register.
 *
 * @return  crcreg: The selected CRC register value.
 */
uint16_t SPI_GetCRC(SPI_TypeDef *SPIx, uint8_t SPI_CRC)
{
    uint16_t crcreg = 0;

    if(SPI_CRC != SPI_CRC_Rx)
    {
        crcreg = SPIx->TCRCR;
    }
    else
    {
        crcreg = SPIx->RCRCR;
    }

    return crcreg;
}

/*********************************************************************
 * @fn      SPI_GetCRCPolynomial
 *
 * @brief   Returns the CRC Polynomial register value for the specified SPI.
 *
 * @param   SPIx - where x can be 1 to select the SPI peripheral.
 *
 * @return  SPIx->CRCR - The CRC Polynomial register value.
 */
uint16_t SPI_GetCRCPolynomial(SPI_TypeDef *SPIx)
{
    return SPIx->CRCR;
}

/*********************************************************************
 * @fn      SPI_BiDirectionalLineConfig
 *
 * @brief   Selects the data transfer direction in bi-directional mode
 *      for the specified SPI.
 *
 * @param   SPIx - where x can be 1 to select the SPI peripheral.
 *          SPI_Direction - specifies the data transfer direction in
 *        bi-directional mode.
 *            SPI_Direction_Tx - Selects Tx transmission direction.
 *            SPI_Direction_Rx - Selects Rx receive direction.
 *
 * @return  none
 */
void SPI_BiDirectionalLineConfig(SPI_TypeDef *SPIx, uint16_t SPI_Direction)
{
    if(SPI_Direction == SPI_Direction_Tx)
    {
        SPIx->CTLR1 |= SPI_Direction_Tx;
    }
    else
    {
        SPIx->CTLR1 &= SPI_Direction_Rx;
    }
}

/*********************************************************************
 * @fn      SPI_I2S_GetFlagStatus
 *
 * @brief   Checks whether the specified SPI flag is set or not.
 *
 * @param   SPIx - where x can be
 *            - 1 in SPI mode.
 *          SPI_I2S_FLAG - specifies the SPI/I2S flag to check.
 *            SPI_I2S_FLAG_TXE - Transmit buffer empty flag.
 *            SPI_I2S_FLAG_RXNE - Receive buffer not empty flag.
 *            SPI_I2S_FLAG_BSY - Busy flag.
 *            SPI_I2S_FLAG_OVR - Overrun flag.
 *            SPI_FLAG_MODF - Mode Fault flag.
 *            SPI_FLAG_CRCERR - CRC Error flag.
 *            I2S_FLAG_UDR - Underrun Error flag.
 *            I2S_FLAG_CHSIDE - Channel Side flag.
 *
 * @return  FlagStatus: SET or RESET.
 */
FlagStatus SPI_I2S_GetFlagStatus(SPI_TypeDef *SPIx, uint16_t SPI_I2S_FLAG)
{
    FlagStatus bitstatus = RESET;

    if((SPIx->STATR & SPI_I2S_FLAG) != (uint16_t)RESET)
    {
        bitstatus = SET;
    }
    else
    {
        bitstatus = RESET;
    }

    return bitstatus;
}

/*********************************************************************
 * @fn      SPI_I2S_ClearFlag
 *
 * @brief   Clears the SPIx CRC Error (CRCERR) flag.
 *
 * @param   SPIx - where x can be
 *            - 1 in SPI mode.
 *          SPI_I2S_FLAG - specifies the SPI flag to clear.
 *            SPI_FLAG_CRCERR - CRC Error flag.
 *          Note-
 *          - OVR (OverRun error) flag is cleared by software sequence: a read 
 *          operation to SPI_DATAR register (SPI_I2S_ReceiveData()) followed by a read 
 *          operation to SPI_STATR register (SPI_I2S_GetFlagStatus()).
 *          - UDR (UnderRun error) flag is cleared by a read operation to 
 *          SPI_STATR register (SPI_I2S_GetFlagStatus()).
 *          - MODF (Mode Fault) flag is cleared by software sequence: a read/write 
 *          operation to SPI_STATR register (SPI_I2S_GetFlagStatus()) followed by a 
 *          write operation to SPI_CTLR1 register (SPI_Cmd() to enable the SPI).
 * @return  FlagStatus: SET or RESET.
 */
void SPI_I2S_ClearFlag(SPI_TypeDef *SPIx, uint16_t SPI_I2S_FLAG)
{
    SPIx->STATR = (uint16_t)~SPI_I2S_FLAG;
}

/*********************************************************************
 * @fn      SPI_I2S_GetITStatus
 *
 * @brief   Checks whether the specified SPI interrupt has occurred or not.
 *
 * @param   SPIx - where x can be
 *            - 1 in SPI mode.
 *          SPI_I2S_IT - specifies the SPI interrupt source to check..
 *            SPI_I2S_IT_TXE - Transmit buffer empty interrupt.
 *            SPI_I2S_IT_RXNE - Receive buffer not empty interrupt.
 *            SPI_I2S_IT_OVR - Overrun interrupt.
 *            SPI_IT_MODF - Mode Fault interrupt.
 *            SPI_IT_CRCERR - CRC Error interrupt.
 *            I2S_IT_UDR - Underrun Error interrupt.
 *
 * @return  FlagStatus: SET or RESET.
 */
ITStatus SPI_I2S_GetITStatus(SPI_TypeDef *SPIx, uint8_t SPI_I2S_IT)
{
    ITStatus bitstatus = RESET;
    uint16_t itpos = 0, itmask = 0, enablestatus = 0;

    itpos = 0x01 << (SPI_I2S_IT & 0x0F);
    itmask = SPI_I2S_IT >> 4;
    itmask = 0x01 << itmask;
    enablestatus = (SPIx->CTLR2 & itmask);

    if(((SPIx->STATR & itpos) != (uint16_t)RESET) && enablestatus)
    {
        bitstatus = SET;
    }
    else
    {
        bitstatus = RESET;
    }

    return bitstatus;
}

/*********************************************************************
 * @fn      SPI_I2S_ClearITPendingBit
 *
 * @brief   Clears the SPIx CRC Error (CRCERR) interrupt pending bit.
 *
 * @param   SPIx - where x can be
 *            - 1 in SPI mode.
 *          SPI_I2S_IT - specifies the SPI interrupt pending bit to clear.
 *            SPI_IT_CRCERR - CRC Error interrupt.
 *         Note-
 *         - OVR (OverRun Error) interrupt pending bit is cleared by software 
 *         sequence: a read operation to SPI_DATAR register (SPI_I2S_ReceiveData()) 
 *         followed by a read operation to SPI_STATR register (SPI_I2S_GetITStatus()).
 *         - UDR (UnderRun Error) interrupt pending bit is cleared by a read 
 *         operation to SPI_STATR register (SPI_I2S_GetITStatus()).
 *         - MODF (Mode Fault) interrupt pending bit is cleared by software sequence:
 *         a read/write operation to SPI_STATR register (SPI_I2S_GetITStatus()) 
 *         followed by a write operation to SPI_CTLR1 register (SPI_Cmd() to enable 
 *         the SPI).      
 * @return  none
 */
void SPI_I2S_ClearITPendingBit(SPI_TypeDef *SPIx, uint8_t SPI_I2S_IT)
{
    uint16_t itpos = 0;

    itpos = 0x01 << (SPI_I2S_IT & 0x0F);
    SPIx->STATR = (uint16_t)~itpos;
}






