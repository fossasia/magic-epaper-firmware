#ifndef __UC8253_DRV_H__
#define __UC8253_DRV_H__

#include <stdint.h>

void uc8253_switch2cmd();
void uc8253_switch2data();

void uc8253_select();
void uc8253_deselect();

void uc8253_pull_reset();
void uc8253_release_reset();

int uc8253_is_busy();

void uc8253_write(uint8_t byte);
void uc8253_write_blk(uint8_t *buf, int len);

void uc8253_init_hal();

#endif /* __UC8253_DRV_H__ */
