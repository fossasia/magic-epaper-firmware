/********************************** (C) COPYRIGHT  *******************************
 * File Name          : ch32v00x_opa.c
 * Author             : WCH
 * Version            : V1.0.0
 * Date               : 2022/08/08
 * Description        : This file provides all the OPA firmware functions.
 *********************************************************************************
 * Copyright (c) 2021 Nanjing Qinheng Microelectronics Co., Ltd.
 * Attention: This software (modified or not) and binary are used for 
 * microcontroller manufactured by Nanjing Qinheng Microelectronics.
 *******************************************************************************/
#include <ch32v00x_opa.h>


/*********************************************************************
 * @fn      OPA_DeInit
 *
 * @brief   Deinitializes the OPA peripheral registers to their default
 *        reset values.
 *
 * @return  none
 */
void OPA_DeInit(void)
{
    EXTEN->EXTEN_CTR &= ~(uint32_t)(7 << 16);
}

/*********************************************************************
 * @fn      OPA_Init
 *
 * @brief   Initializes the OPA peripheral according to the specified
 *        parameters in the OPA_InitStruct.
 *
 * @param   OPA_InitStruct - pointer to a OPA_InitTypeDef structure
 *
 * @return  none
 */
void OPA_Init(OPA_InitTypeDef *OPA_InitStruct)
{
    uint32_t tmp = 0;
    tmp = EXTEN->EXTEN_CTR;
    tmp &= ~(uint32_t)(3<<17);
    tmp |= (OPA_InitStruct->PSEL << 18) | (OPA_InitStruct->NSEL << 17);
    EXTEN->EXTEN_CTR = tmp;
}

/*********************************************************************
 * @fn      OPA_StructInit
 *
 * @brief   Fills each OPA_StructInit member with its reset value.
 *
 * @param   OPA_StructInit - pointer to a OPA_InitTypeDef structure
 *
 * @return  none
 */
void OPA_StructInit(OPA_InitTypeDef *OPA_InitStruct)
{
    OPA_InitStruct->PSEL = CHP0;
    OPA_InitStruct->NSEL = CHN0;
}

/*********************************************************************
 * @fn      OPA_Cmd
 *
 * @brief   Enables or disables the specified OPA peripheral.
 *
 * @param   OPA_NUM - Select OPA
 *            NewState - ENABLE or DISABLE.
 *
 * @return  none
 */
void OPA_Cmd(FunctionalState NewState)
{
    if(NewState == ENABLE)
    {
        EXTEN->EXTEN_CTR |= (uint32_t)(1 << 16);
    }
    else
    {
        EXTEN->EXTEN_CTR &= ~(uint32_t)(1 << 16);
    }
}
