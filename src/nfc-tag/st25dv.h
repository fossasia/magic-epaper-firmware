#ifndef __ST25DV_H__
#define __ST25DV_H__

#include <stdint.h>

extern volatile int ntag_has_new_msg, ntag_msg_len;

void st25dv_dump_sysregs();
void st25dv_dump_usrregs();
void st25dv_dump_allregs();

void st25dv_open_session();
void st25dv_close_session();
int st25dv_is_session_opened();

void st25_enable_FTM();
uint8_t st25dv_has_rf_put_msg();
uint8_t st25dv_mb_len();
int st25dv_gather_msg(uint8_t *buf);

#endif /* __ST25DV_H__ */
