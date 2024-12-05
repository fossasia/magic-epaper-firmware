#include "../mcu/debug.h"
#include "st25dv.h"
#include "drv.h"

/* System configuration memory map */
/* E2=1, E1=1 */
#define GPO1            0x00 // Enable/disable GPO output and GPO ITs for RF events
#define GPO2            0x01 // Enable/disable GPO ITs for I²C events and set Interruption pulse duration
#define EH_MODE         0x02 // Energy harvesting default strategy after Power ON
#define RF_MNGT         0x03 // RF interface state after power ON
#define RFA1SS          0x04 // Area1 RF access protection
#define ENDA1           0x05 // Area 1 ending point
#define RFA2SS          0x06 // Area2 RF access protection
#define ENDA2           0x07 // Area 2 ending point
#define RFA3SS          0x08 // Area3 RF access protection
#define ENDA3           0x09 // Area 3 ending point
#define RFA4SS          0x0A // Area4 RF access protection
#define I2CSS           0x0B // Area 1 to 4 I²C access protection
#define LOCK_CCFILE     0x0C // Blocks 0 and 1 RF Write protection
#define FTM             0x0D // Fast transfer mode authorization and watchdog setting.
#define I2C_CFG         0x0E // I2C target address cfg and enable/disable RF switch off from I2C
#define LOCK_CFG        0x0F // Protect RF Write to system cfg registers
#define LOCK_DSFID      0x10 // DSFID lock status
#define LOCK_AFI        0x11 // AFI lock status
#define DSFID           0x12 // DSFID value
#define AFI             0x13 // AFI value
#define MEM_SIZEl       0x14 // Memory size value in blocks, 2 bytes
#define MEM_SIZEh       0x15 // Memory size value in blocks, 2 bytes
#define BLK_SIZE        0x16 // Block size value in bytes
#define IC_REF          0x17 // IC reference value
#define UID             0x18 // Unique identifier, 8 bytes
#define IC_REV          0x20 // IC revision

#define I2C_PWD         0x0900 // I2C security session password, 8 bytes

/* Dynamic registers memory map */
/* E2 = 0, E1 = 1  */
#define GPO_CTRL_Dyn    0x2000 // GPO control
#define EH_CTRL_Dyn     0x2002 // Energy Harvesting management and usage status
#define RF_MNGT_Dyn     0x2003 // RF interface usage management
#define I2C_SSO_Dyn     0x2004 // I2C security session status
#define IT_STS_Dyn      0x2005 // Interruptions status
#define MB_CTRL_Dyn     0x2006 // Fast transfer mode control and status
#define MB_LEN_Dyn      0x2007 // Length of fast transfer mode message
#define MB_Dyn          0x2008 // Fast transfer mode buffer (256-Bytes)

/* Device Address */
#define SEL_CODE        0b10100010
#define SEL_SYSTEM      0b00001100 | SEL_CODE
#define SEL_USER        0b00000100 | SEL_CODE
// #define R               0b00000000
// #define W               0b00000001

void st25dv_write(uint8_t sel, uint16_t addr, uint8_t val)
{
	st25dv_writes(sel, addr, &val, 1);
}

uint8_t st25dv_read(uint8_t sel, uint16_t addr)
{
	uint8_t val;
	st25dv_reads(sel, addr, &val, 1);
	return val;
}

void st25dv_dump_sysregs()
{
	const int n = 0x21;
	uint8_t regs[n];
	const char *sysreg_names[] = {
		"       GPO1",
		"       GPO2",
		"    EH_MODE",
		"    RF_MNGT",
		"     RFA1SS",
		"      ENDA1",
		"     RFA2SS",
		"      ENDA2",
		"     RFA3SS",
		"      ENDA3",
		"     RFA4SS",
		"      I2CSS",
		"LOCK_CCFILE",
		"        FTM",
		"    I2C_CFG",
		"   LOCK_CFG",
		" LOCK_DSFID",
		"   LOCK_AFI",
		"      DSFID",
		"        AFI",
		"  MEM_SIZEl",
		"  MEM_SIZEh",
		"   BLK_SIZE",
		"     IC_REF",
		"        UID", // 8 bytes
		"     IC_REV"
	};
	st25dv_reads(SEL_SYSTEM, 0x00, regs, n);

	PRINT("System regs:\n");
	for (int i=0; i<n-9; i++) {
		PRINT("%s: %02X,%c", sysreg_names[i], regs[i], (i+1)%4 ? ' ' : '\n');
	}

	PRINT("%s: ", sysreg_names[UID]);
	for (int i=0; i<8; i++) {
		PRINT("%02X ", regs[i]);
	}
	PRINT("\n");

	PRINT("%s: %02X\n", sysreg_names[0x19], regs[IC_REV]);

	PRINT("\n");
}

void st25dv_dump_usrregs()
{
	const int n = 8;
	uint8_t regs[n];
	const char *usrreg_names[] = {
		"GPO_CTRL_Dyn",
		"dummy",
		" EH_CTRL_Dyn",
		" RF_MNGT_Dyn",
		" I2C_SSO_Dyn",
		"  IT_STS_Dyn",
		" MB_CTRL_Dyn",
		"  MB_LEN_Dyn"
	};
	st25dv_reads(SEL_USER, GPO_CTRL_Dyn, regs, n);

	PRINT("User regs:\n");
	for (int i=0; i<n; i++) {
		if (i == 1) continue;
		PRINT("%s: %02X,%c", usrreg_names[i], regs[i], (i+1)%4 ? ' ' : '\n');
	}

	PRINT("\n");
}

void st25dv_dump_allregs()
{
	st25dv_dump_sysregs();
	st25dv_dump_usrregs();
}

int st25dv_is_session_opened()
{
	uint8_t reg;
	st25dv_reads(SEL_USER, I2C_SSO_Dyn, &reg, 1);
	return reg & 0x01;
}

void st25dv_open_session()
{
	uint8_t pw[17] = {
		0, 0, 0, 0, 0, 0, 0, 0,
		0x09, // validation code
		0, 0, 0, 0, 0, 0, 0, 0
	};
	st25dv_writes(SEL_SYSTEM, 0x0900, pw, 17);
}

void st25dv_close_session()
{
	uint8_t wrong_pw[17] = {
		-1, -1, -1, -1, -1, -1, -1, -1,
		0x09, // validation code
		-1, -1, -1, -1, -1, -1, -1, -1
	};
	st25dv_writes(SEL_SYSTEM, 0x0900, wrong_pw, 17);
}

// Enable Fast Transfer Mode
void st25_enable_FTM()
{
	uint8_t ftm = st25dv_read(SEL_SYSTEM, FTM);
	if (!(ftm & 0x01)) {
		st25dv_open_session();
		st25dv_write(SEL_SYSTEM, FTM, 0x01); // MB_MODE = 1, MB_WDG = 0
		st25dv_close_session();
	}

	st25dv_write(SEL_USER, MB_CTRL_Dyn, 0x01); // MB_EN = 1
}

uint8_t st25dv_has_rf_put_msg()
{
	return st25dv_read(SEL_USER, MB_CTRL_Dyn) & 0x04;
}

uint8_t st25dv_mb_len()
{
	return st25dv_read(SEL_USER, MB_LEN_Dyn);
}

// FIX: buf size
int st25dv_gather_msg(uint8_t *buf)
{
	int len = st25dv_mb_len() + 1;
	st25dv_reads(SEL_USER, MB_Dyn, buf, len);
	return len;
}