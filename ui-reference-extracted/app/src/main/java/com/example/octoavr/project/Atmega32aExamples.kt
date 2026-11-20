package com.example.octoavr.project

data class ProjectExampleTemplate(
    val id: String,
    val title: String,
    val category: String,
    val summary: String,
    val pinsNote: String,
    val files: Map<String, String>,
    val defaultActiveFile: String
)

object Atmega32aExamples {

    val all: List<ProjectExampleTemplate> = listOf(
        ProjectExampleTemplate(
            id = "led_blink",
            title = "1. LED Blinking",
            category = "Basics",
            summary = "Toggles PB0 at 1 Hz on ATmega32A using avr/io.h and _delay_ms().",
            pinsNote = "Connect LED + 330R resistor to PB0 (Pin 1) -> GND",
            defaultActiveFile = "main.c",
            files = mapOf(
                "main.c" to """
                    #ifndef F_CPU
                    #define F_CPU 8000000UL
                    #endif

                    #include <avr/io.h>
                    #include <util/delay.h>

                    int main(void) {
                        /* Configure PB0 as output */
                        DDRB |= (1 << PB0);

                        while (1) {
                            PORTB ^= (1 << PB0);
                            _delay_ms(500);
                        }
                        return 0;
                    }
                """.trimIndent() + "\n"
            )
        ),
        ProjectExampleTemplate(
            id = "gpio_io",
            title = "2. GPIO Input / Output",
            category = "GPIO",
            summary = "Mirrors a 4-bit DIP switch on PORTA (PA0..PA3) to LEDs on PORTC (PC0..PC3).",
            pinsNote = "Inputs: PA0..PA3 (pull-up enabled), Outputs: PC0..PC3",
            defaultActiveFile = "main.c",
            files = mapOf(
                "main.c" to """
                    #ifndef F_CPU
                    #define F_CPU 8000000UL
                    #endif

                    #include <avr/io.h>
                    #include <stdint.h>

                    int main(void) {
                        /* PA0..PA3 as input with internal pull-ups */
                        DDRA &= ~0x0F;
                        PORTA |= 0x0F;

                        /* PC0..PC3 as outputs */
                        DDRC |= 0x0F;

                        while (1) {
                            uint8_t switches = (~PINA) & 0x0F;
                            PORTC = (PORTC & 0xF0) | switches;
                        }
                        return 0;
                    }
                """.trimIndent() + "\n"
            )
        ),
        ProjectExampleTemplate(
            id = "push_button",
            title = "3. Push-Button Toggle",
            category = "GPIO",
            summary = "Debounced push-button on PD2 toggles latched LED state on PB0.",
            pinsNote = "Button: PD2 -> GND (internal pull-up), LED: PB0",
            defaultActiveFile = "main.c",
            files = mapOf(
                "main.c" to """
                    #ifndef F_CPU
                    #define F_CPU 8000000UL
                    #endif

                    #include <avr/io.h>
                    #include <util/delay.h>
                    #include <stdint.h>

                    static uint8_t button_pressed(void) {
                        if ((PIND & (1 << PD2)) == 0) {
                            _delay_ms(20);
                            if ((PIND & (1 << PD2)) == 0) {
                                return 1;
                            }
                        }
                        return 0;
                    }

                    int main(void) {
                        DDRB |= (1 << PB0);
                        DDRD &= ~(1 << PD2);
                        PORTD |= (1 << PD2); /* Enable pull-up */

                        while (1) {
                            if (button_pressed()) {
                                PORTB ^= (1 << PB0);
                                while ((PIND & (1 << PD2)) == 0) {
                                    _delay_ms(10);
                                }
                            }
                        }
                        return 0;
                    }
                """.trimIndent() + "\n"
            )
        ),
        ProjectExampleTemplate(
            id = "ext_interrupt",
            title = "4. External Interrupt INT0",
            category = "Interrupts",
            summary = "Falling edge on INT0 (PD2) triggers ISR(INT0_vect) to toggle PB1.",
            pinsNote = "INT0: PD2 (Pin 16), Status LED: PB1 (Pin 2)",
            defaultActiveFile = "main.c",
            files = mapOf(
                "main.c" to """
                    #ifndef F_CPU
                    #define F_CPU 8000000UL
                    #endif

                    #include <avr/io.h>
                    #include <avr/interrupt.h>
                    #include <util/delay.h>

                    volatile uint8_t int0_flag = 0;

                    ISR(INT0_vect) {
                        PORTB ^= (1 << PB1);
                        int0_flag = 1;
                    }

                    int main(void) {
                        DDRB |= (1 << PB0) | (1 << PB1);
                        DDRD &= ~(1 << PD2);
                        PORTD |= (1 << PD2);

                        /* Falling edge of INT0 generates interrupt */
                        MCUCR |= (1 << ISC01);
                        MCUCR &= ~(1 << ISC00);
                        GICR |= (1 << INT0);

                        sei();

                        while (1) {
                            PORTB ^= (1 << PB0);
                            _delay_ms(250);
                        }
                        return 0;
                    }
                """.trimIndent() + "\n"
            )
        ),
        ProjectExampleTemplate(
            id = "timer0_timer1",
            title = "5. Timer0 & Timer1 CTC",
            category = "Timers",
            summary = "Uses Timer0 overflow for 1ms tick and Timer1 CTC mode for accurate 1Hz pulse.",
            pinsNote = "Timer0 status: PB0, Timer1 1Hz LED: PD5 (OC1A)",
            defaultActiveFile = "main.c",
            files = mapOf(
                "main.c" to """
                    #ifndef F_CPU
                    #define F_CPU 8000000UL
                    #endif

                    #include <avr/io.h>
                    #include <avr/interrupt.h>
                    #include <stdint.h>

                    volatile uint16_t ms_ticks = 0;

                    ISR(TIMER0_COMP_vect) {
                        ms_ticks++;
                        if (ms_ticks >= 500) {
                            ms_ticks = 0;
                            PORTB ^= (1 << PB0);
                        }
                    }

                    static void timer0_init_1ms(void) {
                        /* CTC mode, prescaler 64: 8MHz / 64 / 125 = 1000 Hz */
                        TCCR0 = (1 << WGM01) | (1 << CS01) | (1 << CS00);
                        OCR0 = 124;
                        TIMSK |= (1 << OCIE0);
                    }

                    static void timer1_init_1hz_oc1a(void) {
                        /* Toggle OC1A (PD5) on Compare Match, CTC mode, prescaler 256 */
                        DDRD |= (1 << PD5);
                        TCCR1A = (1 << COM1A0);
                        TCCR1B = (1 << WGM12) | (1 << CS12);
                        OCR1A = 15624; /* 8MHz / (2 * 256 * 15625) = 1 Hz */
                    }

                    int main(void) {
                        DDRB |= (1 << PB0);
                        timer0_init_1ms();
                        timer1_init_1hz_oc1a();
                        sei();

                        while (1) {
                            /* Main loop free for application tasks */
                        }
                        return 0;
                    }
                """.trimIndent() + "\n"
            )
        ),
        ProjectExampleTemplate(
            id = "pwm_fade",
            title = "6. Hardware PWM Fade",
            category = "Timers / PWM",
            summary = "8-bit Fast PWM on Timer0 OC0 (PB3) smoothly ramps LED brightness.",
            pinsNote = "PWM Output: PB3 / OC0 (Pin 4)",
            defaultActiveFile = "main.c",
            files = mapOf(
                "main.c" to """
                    #ifndef F_CPU
                    #define F_CPU 8000000UL
                    #endif

                    #include <avr/io.h>
                    #include <util/delay.h>
                    #include <stdint.h>

                    static void pwm_oc0_init(void) {
                        DDRB |= (1 << PB3); /* OC0 pin as output */
                        /* Fast PWM, non-inverting mode, prescaler 64 */
                        TCCR0 = (1 << WGM00) | (1 << WGM01) | (1 << COM01) | (1 << CS01) | (1 << CS00);
                        OCR0 = 0;
                    }

                    int main(void) {
                        uint8_t duty = 0;
                        int8_t step = 2;

                        pwm_oc0_init();

                        while (1) {
                            OCR0 = duty;
                            if (duty >= 254) {
                                step = -2;
                            } else if (duty <= 0) {
                                step = 2;
                            }
                            duty = (uint8_t)(duty + step);
                            _delay_ms(10);
                        }
                        return 0;
                    }
                """.trimIndent() + "\n"
            )
        ),
        ProjectExampleTemplate(
            id = "uart_serial",
            title = "7. UART Serial Echo",
            category = "Communication",
            summary = "Configures ATmega32A USART at 9600 baud (8N1) for bidirectional serial.",
            pinsNote = "RXD: PD0 (Pin 14), TXD: PD1 (Pin 15) -> USB-UART adapter",
            defaultActiveFile = "main.c",
            files = mapOf(
                "main.c" to """
                    #ifndef F_CPU
                    #define F_CPU 8000000UL
                    #endif

                    #include <avr/io.h>
                    #include <stdint.h>

                    #define BAUD 9600UL
                    #define MYUBRR ((F_CPU / (16UL * BAUD)) - 1UL)

                    static void uart_init(uint16_t ubrr) {
                        UBRRH = (uint8_t)(ubrr >> 8);
                        UBRRL = (uint8_t)ubrr;
                        UCSRB = (1 << RXEN) | (1 << TXEN);
                        /* URSEL must be 1 when writing UCSRC on ATmega32A */
                        UCSRC = (1 << URSEL) | (1 << UCSZ1) | (1 << UCSZ0);
                    }

                    static void uart_tx(uint8_t data) {
                        while (!(UCSRA & (1 << UDRE))) { }
                        UDR = data;
                    }

                    static void uart_print(const char *str) {
                        while (*str) {
                            uart_tx((uint8_t)*str++);
                        }
                    }

                    static uint8_t uart_rx(void) {
                        while (!(UCSRA & (1 << RXC))) { }
                        return UDR;
                    }

                    int main(void) {
                        uart_init((uint16_t)MYUBRR);
                        uart_print("OctoAVR32A UART Ready\r\n");

                        while (1) {
                            uint8_t ch = uart_rx();
                            uart_tx(ch);
                        }
                        return 0;
                    }
                """.trimIndent() + "\n"
            )
        ),
        ProjectExampleTemplate(
            id = "spi_master",
            title = "8. SPI Master Transfer",
            category = "Communication",
            summary = "Initializes ATmega32A SPI Master on PORTB and shifts bytes out.",
            pinsNote = "SS: PB4, MOSI: PB5, MISO: PB6, SCK: PB7",
            defaultActiveFile = "main.c",
            files = mapOf(
                "main.c" to """
                    #ifndef F_CPU
                    #define F_CPU 8000000UL
                    #endif

                    #include <avr/io.h>
                    #include <util/delay.h>
                    #include <stdint.h>

                    static void spi_master_init(void) {
                        /* Set MOSI (PB5), SCK (PB7), and SS (PB4) as outputs */
                        DDRB |= (1 << PB4) | (1 << PB5) | (1 << PB7);
                        DDRB &= ~(1 << PB6); /* MISO input */
                        PORTB |= (1 << PB4); /* Keep SS high */

                        /* Enable SPI, Master mode, F_CPU / 16 */
                        SPCR = (1 << SPE) | (1 << MSTR) | (1 << SPR0);
                    }

                    static uint8_t spi_transfer(uint8_t data) {
                        SPDR = data;
                        while (!(SPSR & (1 << SPIF))) { }
                        return SPDR;
                    }

                    int main(void) {
                        uint8_t counter = 0;
                        spi_master_init();

                        while (1) {
                            PORTB &= ~(1 << PB4);
                            (void)spi_transfer(counter++);
                            PORTB |= (1 << PB4);
                            _delay_ms(100);
                        }
                        return 0;
                    }
                """.trimIndent() + "\n"
            )
        ),
        ProjectExampleTemplate(
            id = "adc_read",
            title = "9. ADC 10-bit Conversion",
            category = "Analog",
            summary = "Reads analog voltage on ADC0 (PA0) and displays upper 8 bits on PORTB.",
            pinsNote = "Analog Input: PA0 (Pin 40), Output bargraph: PORTB",
            defaultActiveFile = "main.c",
            files = mapOf(
                "main.c" to """
                    #ifndef F_CPU
                    #define F_CPU 8000000UL
                    #endif

                    #include <avr/io.h>
                    #include <util/delay.h>
                    #include <stdint.h>

                    static void adc_init(void) {
                        /* AVCC reference, right-adjusted result */
                        ADMUX = (1 << REFS0);
                        /* Enable ADC, prescaler 64 -> 125 kHz ADC clock @ 8 MHz */
                        ADCSRA = (1 << ADEN) | (1 << ADPS2) | (1 << ADPS1);
                    }

                    static uint16_t adc_read(uint8_t channel) {
                        ADMUX = (ADMUX & 0xF8) | (channel & 0x07);
                        ADCSRA |= (1 << ADSC);
                        while (ADCSRA & (1 << ADSC)) { }
                        return ADC;
                    }

                    int main(void) {
                        DDRB = 0xFF;
                        adc_init();

                        while (1) {
                            uint16_t sample = adc_read(0);
                            PORTB = (uint8_t)(sample >> 2);
                            _delay_ms(50);
                        }
                        return 0;
                    }
                """.trimIndent() + "\n"
            )
        ),
        ProjectExampleTemplate(
            id = "multifile_hal",
            title = "10. Multi-File HAL Project",
            category = "Architecture",
            summary = "Modular ATmega32A project with header include/hal_gpio.h, src/hal_gpio.c, and main.c.",
            pinsNote = "Status LED: PB0, Heartbeat LED: PB1, EEPROM boot counter",
            defaultActiveFile = "main.c",
            files = mapOf(
                "include/hal_gpio.h" to """
                    #ifndef HAL_GPIO_H
                    #define HAL_GPIO_H

                    #include <stdint.h>

                    void hal_led_init(uint8_t mask);
                    void hal_led_toggle(uint8_t mask);
                    void hal_led_write(uint8_t mask, uint8_t state);

                    #endif /* HAL_GPIO_H */
                """.trimIndent() + "\n",
                "src/hal_gpio.c" to """
                    #include <avr/io.h>
                    #include "hal_gpio.h"

                    void hal_led_init(uint8_t mask) {
                        DDRB |= mask;
                        PORTB &= ~mask;
                    }

                    void hal_led_toggle(uint8_t mask) {
                        PORTB ^= mask;
                    }

                    void hal_led_write(uint8_t mask, uint8_t state) {
                        if (state) {
                            PORTB |= mask;
                        } else {
                            PORTB &= ~mask;
                        }
                    }
                """.trimIndent() + "\n",
                "main.c" to """
                    #ifndef F_CPU
                    #define F_CPU 8000000UL
                    #endif

                    #include <avr/io.h>
                    #include <avr/eeprom.h>
                    #include <util/delay.h>
                    #include "hal_gpio.h"

                    uint8_t EEMEM ee_boot_count = 0;

                    int main(void) {
                        uint8_t boots = eeprom_read_byte(&ee_boot_count);
                        eeprom_update_byte(&ee_boot_count, (uint8_t)(boots + 1));

                        hal_led_init((1 << PB0) | (1 << PB1));
                        hal_led_write((1 << PB1), boots & 0x01);

                        while (1) {
                            hal_led_toggle((1 << PB0));
                            _delay_ms(300);
                        }
                        return 0;
                    }
                """.trimIndent() + "\n"
            )
        )
    )
}
