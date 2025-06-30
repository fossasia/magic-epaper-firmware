######################################
# target
######################################
TARGET = ch32v003f4p6
TARGET_DEFS = -DSYSCLK_FREQ_24MHZ_HSI=HSI_VALUE

######################################
# building variables
######################################
# debug build?
DEBUG ?= 1
DEBUG_BAUD ?= 921600
# optimization for size
OPT = -Os


#######################################
# paths
#######################################
# Build path
BUILD_DIR = build

######################################
# source
######################################
# C sources
C_SOURCES = \
CH32V-EVT/Peripheral/src/ch32v00x_gpio.c \
CH32V-EVT/Peripheral/src/ch32v00x_pwr.c \
CH32V-EVT/Peripheral/src/ch32v00x_opa.c \
CH32V-EVT/Peripheral/src/ch32v00x_dbgmcu.c \
CH32V-EVT/Peripheral/src/ch32v00x_adc.c \
CH32V-EVT/Peripheral/src/ch32v00x_exti.c \
CH32V-EVT/Peripheral/src/ch32v00x_dma.c \
CH32V-EVT/Peripheral/src/ch32v00x_tim.c \
CH32V-EVT/Peripheral/src/ch32v00x_rcc.c \
CH32V-EVT/Peripheral/src/ch32v00x_i2c.c \
CH32V-EVT/Peripheral/src/ch32v00x_wwdg.c \
CH32V-EVT/Peripheral/src/ch32v00x_misc.c \
CH32V-EVT/Peripheral/src/ch32v00x_iwdg.c \
CH32V-EVT/Peripheral/src/ch32v00x_usart.c \
CH32V-EVT/Peripheral/src/ch32v00x_spi.c \
CH32V-EVT/Peripheral/src/ch32v00x_flash.c \
CH32V-EVT/Core/core_riscv.c \
\
src/main.c \
src/mcu/ch32v00x-hal.c \
src/mcu/ch32v00x-sys.c \
src/mcu/ch32v00x-it.c \
src/mcu/ch32v00x-debug.c \
src/epd/uc8253.c \
src/epd/ch32v00x-drv.c \
src/nfc-tag/st25dv.c \
src/nfc-tag/ch32v00x-drv.c \

# ASM sources
ASM_SOURCES =  \
CH32V-EVT/Startup/startup_ch32v00x.S

#######################################
# binaries
#######################################
PREFIX ?= riscv-none-embed-

CC = $(PREFIX)gcc
AS = $(PREFIX)gcc -x assembler-with-cpp
CP = $(PREFIX)objcopy
SZ = $(PREFIX)size

HEX = $(CP) -O ihex
BIN = $(CP) -O binary -S

#######################################
# CFLAGS
#######################################
# cpu
CPU = -march=rv32ec -mabi=ilp32e -msmall-data-limit=8 

# For gcc v12 and above
# CPU = -march=rv32imac_zicsr -mabi=ilp32 -msmall-data-limit=8

# mcu
MCU = $(CPU) $(FPU) $(FLOAT-ABI)

# AS includes
AS_INCLUDES = 

# C includes
C_INCLUDES =  \
-ICH32V-EVT/Peripheral/inc \
-ICH32V-EVT/Debug \
-ICH32V-EVT/Core \

# compile gcc flags
ASFLAGS = $(MCU) $(AS_INCLUDES) $(OPT) -Wall -fdata-sections -ffunction-sections

CFLAGS = $(MCU) $(C_INCLUDES) $(OPT) -Wall -fdata-sections -ffunction-sections

ifeq ($(DEBUG), 1)
CFLAGS += -g -gdwarf-2
TARGET_DEFS += -DDEBUG=1
TARGET_DEFS += -DDEBUG_BAUD=$(DEBUG_BAUD)
endif


# Generate dependency information
CFLAGS += -MMD -MP -MF"$(@:%.o=%.d)"

CFLAGS += $(TARGET_DEFS)

#######################################
# LDFLAGS
#######################################
# link script
LDSCRIPT = CH32V-EVT/Ld/Link.ld 

# libraries
LIBS = -lc -lm -lnosys
LIBDIR = 
LDFLAGS = $(MCU) -mno-save-restore -fmessage-length=0 -fsigned-char -ffunction-sections -fdata-sections -Wunused -Wuninitialized -T $(LDSCRIPT) -nostartfiles -Xlinker --gc-sections -Wl,-Map=$(BUILD_DIR)/$(TARGET).map --specs=nano.specs $(LIBS)

# default action: build all
all: $(BUILD_DIR)/$(TARGET).elf $(BUILD_DIR)/$(TARGET).hex $(BUILD_DIR)/$(TARGET).bin


#######################################
# build the application
#######################################
# list of objects
OBJECTS = $(addprefix $(BUILD_DIR)/, $(C_SOURCES:.c=.o))

# list of ASM program objects
OBJECTS += $(addprefix $(BUILD_DIR)/, $(ASM_SOURCES:.S=.o))

$(BUILD_DIR)/%.o: %.c Makefile
	@mkdir -pv $(dir $@)
	$(CC) -c $(CFLAGS) -Wa,-a,-ad,-alms=$(@:.o=.lst) $< -o $@

$(BUILD_DIR)/%.o: %.S Makefile
	@mkdir -pv $(dir $@)
	$(AS) -c $(CFLAGS) $< -o $@

$(BUILD_DIR)/$(TARGET).elf: $(OBJECTS) Makefile
	$(CC) $(OBJECTS) $(LDFLAGS) -o $@
	$(SZ) $@

$(BUILD_DIR)/%.hex: $(BUILD_DIR)/%.elf | $(BUILD_DIR)
	$(HEX) $< $@
	
$(BUILD_DIR)/%.bin: $(BUILD_DIR)/%.elf | $(BUILD_DIR)
	$(BIN) $< $@	
	
$(BUILD_DIR):
	mkdir $@		

#######################################
# Program
#######################################
WCH_OPENOCD = ../MRS_Toolchain_Linux_x64_V1.92/OpenOCD/bin/openocd
program: $(BUILD_DIR)/$(TARGET).elf 
	../MRS_Toolchain_Linux_x64_V1.92/OpenOCD/bin/wch-riscv.cfg -c init -c halt -c "program $^ verify 0x00000000 verify reset exit" -c exit

wlink: $(BUILD_DIR)/$(TARGET).bin
	wlink flash --address 0x08000000 $(BUILD_DIR)/$(TARGET).bin

isp: $(BUILD_DIR)/$(TARGET).bin
	wchisp flash $(BUILD_DIR)/$(TARGET).bin

#######################################
# clean up
#######################################
clean:
	rm -f $(OBJECTS)
	rm -f $(OBJECTS:%.o=%.d)
	rm -f $(OBJECTS:%.o=%.lst)
	rm -f $(BUILD_DIR)/$(TARGET).*
	find $(BUILD_DIR) -type d -empty -delete

#######################################
# dependencies
#######################################
-include $(OBJECTS:%.o=%.d)

# *** EOF ***
