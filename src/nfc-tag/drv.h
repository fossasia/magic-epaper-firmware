#ifndef __ST25DV_DRV_H__
#define __ST25DV_DRV_H__

#include <stdint.h>

void st25dv_reads(uint8_t sel, uint16_t addr, uint8_t *val, int size);
void st25dv_writes(uint8_t sel, uint16_t addr, uint8_t *val, int size);

#endif /* __ST25DV_DRV_H__ */
