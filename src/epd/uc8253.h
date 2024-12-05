#ifndef __uc8253_H__
#define __uc8253_H__

#include <stdint.h>

void uc8253_send(uint8_t byte);
void uc8253_send_blk(uint8_t *bytes, uint16_t len);

void uc8253_cmd(uint8_t reg);
void uc8253_cmd_params(uint8_t *cmd_and_params, uint16_t len);

/** Below are built-in functions for manipulating epd in firmware, built on 
 *  top of functions above
 */

void uc8253_gotoxy(uint16_t x, uint16_t y);
void uc8253_set_resolution(uint16_t w, uint16_t h);
void uc8253_clear_mem(void);
void uc8253_refresh_poll();

void uc8253_init();

#endif /* __uc8253_H__ */
