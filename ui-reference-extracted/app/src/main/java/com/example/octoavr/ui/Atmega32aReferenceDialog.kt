package com.example.octoavr.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.OctoAbyssNavy
import com.example.ui.theme.OctoBorderSubtle
import com.example.ui.theme.OctoCyan
import com.example.ui.theme.OctoError
import com.example.ui.theme.OctoSuccess
import com.example.ui.theme.OctoSurfaceCard
import com.example.ui.theme.OctoSurfaceDark
import com.example.ui.theme.OctoSurfaceElevated
import com.example.ui.theme.OctoTextMuted
import com.example.ui.theme.OctoTextPrimary
import com.example.ui.theme.OctoTextSecondary
import com.example.ui.theme.OctoWarning

private enum class PinKind {
    POWER,
    GROUND,
    RESET,
    CLOCK,
    PORTB,
    PORTC,
    PORTD,
    PORTA
}

private data class Dip40Pin(
    val number: Int,
    val name: String,
    val functions: String,
    val kind: PinKind
)

private data class RegisterGroup(
    val peripheral: String,
    val registers: String,
    val notes: String
)

data class AvrCodeSnippet(
    val title: String,
    val subtitle: String,
    val code: String
)

private object Atmega32aReferenceData {
    val leftPins = listOf(
        Dip40Pin(1, "PB0", "XCK / T0", PinKind.PORTB),
        Dip40Pin(2, "PB1", "T1", PinKind.PORTB),
        Dip40Pin(3, "PB2", "INT2 / AIN0", PinKind.PORTB),
        Dip40Pin(4, "PB3", "OC0 / AIN1", PinKind.PORTB),
        Dip40Pin(5, "PB4", "SS (SPI)", PinKind.PORTB),
        Dip40Pin(6, "PB5", "MOSI (ISP)", PinKind.PORTB),
        Dip40Pin(7, "PB6", "MISO (ISP)", PinKind.PORTB),
        Dip40Pin(8, "PB7", "SCK (ISP)", PinKind.PORTB),
        Dip40Pin(9, "RESET", "Active Low", PinKind.RESET),
        Dip40Pin(10, "VCC", "+2.7V..5.5V", PinKind.POWER),
        Dip40Pin(11, "GND", "0V Ground", PinKind.GROUND),
        Dip40Pin(12, "XTAL2", "Oscillator", PinKind.CLOCK),
        Dip40Pin(13, "XTAL1", "Oscillator", PinKind.CLOCK),
        Dip40Pin(14, "PD0", "RXD (UART)", PinKind.PORTD),
        Dip40Pin(15, "PD1", "TXD (UART)", PinKind.PORTD),
        Dip40Pin(16, "PD2", "INT0", PinKind.PORTD),
        Dip40Pin(17, "PD3", "INT1", PinKind.PORTD),
        Dip40Pin(18, "PD4", "OC1B", PinKind.PORTD),
        Dip40Pin(19, "PD5", "OC1A (PWM)", PinKind.PORTD),
        Dip40Pin(20, "PD6", "ICP1", PinKind.PORTD)
    )

    val rightPins = listOf(
        Dip40Pin(40, "PA0", "ADC0", PinKind.PORTA),
        Dip40Pin(39, "PA1", "ADC1", PinKind.PORTA),
        Dip40Pin(38, "PA2", "ADC2", PinKind.PORTA),
        Dip40Pin(37, "PA3", "ADC3", PinKind.PORTA),
        Dip40Pin(36, "PA4", "ADC4", PinKind.PORTA),
        Dip40Pin(35, "PA5", "ADC5", PinKind.PORTA),
        Dip40Pin(34, "PA6", "ADC6", PinKind.PORTA),
        Dip40Pin(33, "PA7", "ADC7", PinKind.PORTA),
        Dip40Pin(32, "AREF", "ADC Ref", PinKind.POWER),
        Dip40Pin(31, "GND", "Analog GND", PinKind.GROUND),
        Dip40Pin(30, "AVCC", "ADC +5V", PinKind.POWER),
        Dip40Pin(29, "PC7", "TOSC2", PinKind.PORTC),
        Dip40Pin(28, "PC6", "TOSC1", PinKind.PORTC),
        Dip40Pin(27, "PC5", "TDI (JTAG)", PinKind.PORTC),
        Dip40Pin(26, "PC4", "TDO (JTAG)", PinKind.PORTC),
        Dip40Pin(25, "PC3", "TMS (JTAG)", PinKind.PORTC),
        Dip40Pin(24, "PC2", "TCK (JTAG)", PinKind.PORTC),
        Dip40Pin(23, "PC1", "SDA (I2C)", PinKind.PORTC),
        Dip40Pin(22, "PC0", "SCL (I2C)", PinKind.PORTC),
        Dip40Pin(21, "PD7", "OC2 (PWM)", PinKind.PORTD)
    )

    val registers = listOf(
        RegisterGroup(
            "GPIO Ports (A, B, C, D)",
            "DDRA/B/C/D • PORTA/B/C/D • PINA/B/C/D",
            "1 in DDRx = Output, 0 = Input. Writing 1 to PORTx when input enables internal pull-up."
        ),
        RegisterGroup(
            "External Interrupts",
            "MCUCR (ISC01..00, ISC11..10) • MCUCSR (ISC2) • GICR (INT0, INT1, INT2)",
            "Enable global interrupts with sei() from <avr/interrupt.h>."
        ),
        RegisterGroup(
            "Timer0 (8-bit)",
            "TCCR0 • TCNT0 • OCR0 • TIMSK (TOIE0, OCIE0)",
            "Fast PWM output on OC0 (PB3). Prescalers: 1, 8, 64, 256, 1024."
        ),
        RegisterGroup(
            "Timer1 (16-bit)",
            "TCCR1A • TCCR1B • TCNT1 • OCR1A • OCR1B • ICR1",
            "CTC mode (WGM12=1) toggles OC1A (PD5) / OC1B (PD4) with hardware precision."
        ),
        RegisterGroup(
            "USART (UART)",
            "UBRRH • UBRRL • UCSRA • UCSRB • UCSRC • UDR",
            "Set URSEL=1 when writing UCSRC! Default 8N1: UCSRC = (1<<URSEL)|(1<<UCSZ1)|(1<<UCSZ0)."
        ),
        RegisterGroup(
            "SPI Bus",
            "SPCR (SPE, MSTR, SPR0/1) • SPSR (SPIF) • SPDR",
            "Master mode requires SS (PB4) configured as output or held HIGH."
        ),
        RegisterGroup(
            "10-bit ADC",
            "ADMUX (REFS1..0, MUX4..0) • ADCSRA (ADEN, ADSC, ADPS2..0) • ADC",
            "Keep ADC clock between 50 kHz and 200 kHz (ADPS2|ADPS1 = /64 => 125 kHz @ 8 MHz)."
        )
    )

    val snippets = listOf(
        AvrCodeSnippet(
            title = "Bit Manipulation Macros",
            subtitle = "SET_BIT, CLR_BIT, TOGGLE_BIT, READ_BIT",
            code = """
                #define SET_BIT(reg, bit)    ((reg) |= (1U << (bit)))
                #define CLR_BIT(reg, bit)    ((reg) &= ~(1U << (bit)))
                #define TOGGLE_BIT(reg, bit) ((reg) ^= (1U << (bit)))
                #define READ_BIT(reg, bit)   (((reg) >> (bit)) & 1U)
            """.trimIndent() + "\n"
        ),
        AvrCodeSnippet(
            title = "INT0 Falling-Edge ISR",
            subtitle = "Requires #include <avr/interrupt.h>",
            code = """
                ISR(INT0_vect) {
                    PORTB ^= (1 << PB0);
                }
            """.trimIndent() + "\n"
        ),
        AvrCodeSnippet(
            title = "UART 9600 Baud Init (8MHz)",
            subtitle = "ATmega32A URSEL + 8N1 config",
            code = """
                static void uart_init_9600(void) {
                    uint16_t ubrr = (F_CPU / (16UL * 9600UL)) - 1UL;
                    UBRRH = (uint8_t)(ubrr >> 8);
                    UBRRL = (uint8_t)ubrr;
                    UCSRB = (1 << RXEN) | (1 << TXEN);
                    UCSRC = (1 << URSEL) | (1 << UCSZ1) | (1 << UCSZ0);
                }
            """.trimIndent() + "\n"
        ),
        AvrCodeSnippet(
            title = "Timer0 1ms CTC Tick (8MHz)",
            subtitle = "Fires TIMER0_COMP_vect every 1 ms",
            code = """
                static void timer0_1ms_init(void) {
                    TCCR0 = (1 << WGM01) | (1 << CS01) | (1 << CS00);
                    OCR0 = 124;
                    TIMSK |= (1 << OCIE0);
                }
            """.trimIndent() + "\n"
        ),
        AvrCodeSnippet(
            title = "ADC 10-bit Read Helper",
            subtitle = "AVCC ref, prescaler /64 @ 8MHz",
            code = """
                static uint16_t adc_read_ch(uint8_t ch) {
                    ADMUX = (1 << REFS0) | (ch & 0x07);
                    ADCSRA = (1 << ADEN) | (1 << ADPS2) | (1 << ADPS1) | (1 << ADSC);
                    while (ADCSRA & (1 << ADSC)) { }
                    return ADC;
                }
            """.trimIndent() + "\n"
        )
    )
}

@Composable
fun Atmega32aReferenceDialog(
    cpuFrequencyHz: Long = 8_000_000L,
    onInsertSnippet: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Pinout", "Registers", "Snippets", "Calc")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OctoSurfaceElevated,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DeveloperBoard, null, tint = OctoCyan)
                Spacer(Modifier.width(8.dp))
                Text("ATmega32A Reference", color = OctoTextPrimary)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = OctoAbyssNavy,
                    contentColor = OctoCyan,
                    indicator = { positions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(positions[selectedTab]),
                            color = OctoCyan
                        )
                    }
                ) {
                    tabs.forEachIndexed { idx, title ->
                        Tab(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            text = {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selectedTab == idx) OctoCyan else OctoTextSecondary
                                )
                            }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                when (selectedTab) {
                    0 -> Dip40PinoutView()
                    1 -> RegistersReferenceView()
                    2 -> SnippetsInsertView(
                        onInsert = { code ->
                            onInsertSnippet(code)
                            onDismiss()
                        }
                    )
                    3 -> AvrHardwareCalculatorsView(
                        cpuFrequencyHz = cpuFrequencyHz,
                        onInsert = { code ->
                            onInsertSnippet(code)
                            onDismiss()
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = OctoCyan)
            }
        }
    )
}

@Composable
private fun Dip40PinoutView() {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 390.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        item {
            Text(
                "ATmega32A PDIP-40 • 32KB Flash • 2KB SRAM • 1KB EEPROM",
                style = MaterialTheme.typography.labelSmall,
                color = OctoTextSecondary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        items(Atmega32aReferenceData.leftPins.indices.toList()) { rowIdx ->
            val left = Atmega32aReferenceData.leftPins[rowIdx]
            val right = Atmega32aReferenceData.rightPins[rowIdx]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PinBadge(pin = left, alignRight = true, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(28.dp)
                        .background(OctoAbyssNavy, RoundedCornerShape(2.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("•", color = OctoTextMuted, style = MaterialTheme.typography.labelSmall)
                }
                PinBadge(pin = right, alignRight = false, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PinBadge(pin: Dip40Pin, alignRight: Boolean, modifier: Modifier = Modifier) {
    val accent = when (pin.kind) {
        PinKind.POWER -> OctoError
        PinKind.GROUND -> OctoTextMuted
        PinKind.RESET -> OctoWarning
        PinKind.CLOCK -> OctoSuccess
        PinKind.PORTB, PinKind.PORTD -> OctoCyan
        PinKind.PORTC, PinKind.PORTA -> Color(0xFF7DD3FC)
    }
    Surface(
        color = OctoSurfaceDark,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            horizontalAlignment = if (alignRight) Alignment.End else Alignment.Start
        ) {
            Text(
                text = if (alignRight) "${pin.name} [${pin.number}]" else "[${pin.number}] ${pin.name}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = accent,
                textAlign = if (alignRight) TextAlign.End else TextAlign.Start
            )
            Text(
                text = pin.functions,
                style = MaterialTheme.typography.bodySmall,
                color = OctoTextSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun RegistersReferenceView() {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 390.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(Atmega32aReferenceData.registers) { grp ->
            Surface(
                color = OctoSurfaceDark,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, OctoBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text(grp.peripheral, style = MaterialTheme.typography.titleSmall, color = OctoCyan)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        grp.registers,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = OctoTextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(grp.notes, style = MaterialTheme.typography.bodySmall, color = OctoTextSecondary)
                }
            }
        }
    }
}

@Composable
private fun SnippetsInsertView(onInsert: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 390.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(Atmega32aReferenceData.snippets) { snippet ->
            Surface(
                color = OctoSurfaceCard,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, OctoBorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onInsert(snippet.code) }
                    .testTag("snippet_${snippet.title}")
            ) {
                Column(Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(snippet.title, style = MaterialTheme.typography.titleSmall, color = OctoCyan)
                            Text(snippet.subtitle, style = MaterialTheme.typography.bodySmall, color = OctoTextSecondary)
                        }
                        Icon(
                            Icons.Default.AddCircleOutline,
                            "Insert Snippet",
                            tint = OctoCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = OctoBorderSubtle)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        snippet.code.trimEnd(),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = OctoTextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun AvrHardwareCalculatorsView(
    cpuFrequencyHz: Long,
    onInsert: (String) -> Unit
) {
    var selectedBaud by remember { mutableIntStateOf(9600) }
    var selectedTimerMs by remember { mutableIntStateOf(10) }

    val bauds = listOf(4800, 9600, 19200, 38400, 57600, 115200)
    val periodsMs = listOf(1, 5, 10, 50, 100, 500)

    val ubrrNormal = ((cpuFrequencyHz / (16.0 * selectedBaud)) - 1.0).let {
        kotlin.math.round(it).toInt().coerceIn(0, 4095)
    }
    val actualBaudNormal = cpuFrequencyHz / (16.0 * (ubrrNormal + 1))
    val errNormalPct = ((actualBaudNormal - selectedBaud) / selectedBaud) * 100.0

    val ubrrU2x = ((cpuFrequencyHz / (8.0 * selectedBaud)) - 1.0).let {
        kotlin.math.round(it).toInt().coerceIn(0, 4095)
    }
    val actualBaudU2x = cpuFrequencyHz / (8.0 * (ubrrU2x + 1))
    val errU2xPct = ((actualBaudU2x - selectedBaud) / selectedBaud) * 100.0

    val useU2x = kotlin.math.abs(errU2xPct) < kotlin.math.abs(errNormalPct) && kotlin.math.abs(errNormalPct) > 1.5
    val chosenUbrr = if (useU2x) ubrrU2x else ubrrNormal

    // Timer1 CTC 16-bit calculation for target ms
    val prescalers = listOf(1, 8, 64, 256, 1024)
    val timer1Match = prescalers.firstNotNullOfOrNull { p ->
        val ticks = kotlin.math.round((cpuFrequencyHz * (selectedTimerMs / 1000.0)) / p).toLong() - 1L
        if (ticks in 1L..65535L) p to ticks else null
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 390.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "Using project F_CPU = ${cpuFrequencyHz / 1_000_000.0} MHz ($cpuFrequencyHz Hz)",
                style = MaterialTheme.typography.labelSmall,
                color = OctoCyan
            )
        }

        item {
            Surface(
                color = OctoSurfaceDark,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, OctoBorderSubtle)
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("USART Baud Calculator", style = MaterialTheme.typography.titleSmall, color = OctoCyan)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        bauds.take(3).forEach { b ->
                            Surface(
                                color = if (selectedBaud == b) OctoCyan.copy(alpha = 0.2f) else OctoAbyssNavy,
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, if (selectedBaud == b) OctoCyan else OctoBorderSubtle),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedBaud = b }
                            ) {
                                Text(
                                    "$b",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selectedBaud == b) OctoCyan else OctoTextSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 5.dp)
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        bauds.drop(3).forEach { b ->
                            Surface(
                                color = if (selectedBaud == b) OctoCyan.copy(alpha = 0.2f) else OctoAbyssNavy,
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, if (selectedBaud == b) OctoCyan else OctoBorderSubtle),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedBaud = b }
                            ) {
                                Text(
                                    "$b",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selectedBaud == b) OctoCyan else OctoTextSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 5.dp)
                                )
                            }
                        }
                    }
                    Text(
                        "Normal: UBRR=$ubrrNormal (err ${"%.2f".format(errNormalPct)}%) • U2X: UBRR=$ubrrU2x (err ${"%.2f".format(errU2xPct)}%)",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = if (kotlin.math.abs(if (useU2x) errU2xPct else errNormalPct) <= 2.0) OctoSuccess else OctoWarning
                    )
                    TextButton(
                        onClick = {
                            val u2xLine = if (useU2x) "    UCSRA = (1 << U2X);\n" else ""
                            onInsert(
                                """
                                static void uart_init_${selectedBaud}(void) {
                                ${u2xLine}    UBRRH = (uint8_t)($chosenUbrr >> 8);
                                    UBRRL = (uint8_t)($chosenUbrr & 0xFF);
                                    UCSRB = (1 << RXEN) | (1 << TXEN);
                                    UCSRC = (1 << URSEL) | (1 << UCSZ1) | (1 << UCSZ0);
                                }
                                """.trimIndent() + "\n"
                            )
                        }
                    ) {
                        Text("+ Insert UART ${selectedBaud}bps Init", color = OctoCyan)
                    }
                }
            }
        }

        item {
            Surface(
                color = OctoSurfaceDark,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, OctoBorderSubtle)
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Timer1 CTC Period Calculator", style = MaterialTheme.typography.titleSmall, color = OctoCyan)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        periodsMs.forEach { ms ->
                            Surface(
                                color = if (selectedTimerMs == ms) OctoCyan.copy(alpha = 0.2f) else OctoAbyssNavy,
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, if (selectedTimerMs == ms) OctoCyan else OctoBorderSubtle),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedTimerMs = ms }
                            ) {
                                Text(
                                    "${ms}ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selectedTimerMs == ms) OctoCyan else OctoTextSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 5.dp)
                                )
                            }
                        }
                    }
                    if (timer1Match != null) {
                        val (prescaler, ocr1a) = timer1Match
                        val csBits = when (prescaler) {
                            1 -> "(1 << CS10)"
                            8 -> "(1 << CS11)"
                            64 -> "(1 << CS11) | (1 << CS10)"
                            256 -> "(1 << CS12)"
                            else -> "(1 << CS12) | (1 << CS10)"
                        }
                        Text(
                            "Prescaler /$prescaler • OCR1A = $ocr1a (TIMER1_COMPA_vect)",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = OctoSuccess
                        )
                        TextButton(
                            onClick = {
                                onInsert(
                                    """
                                    static void timer1_ctc_${selectedTimerMs}ms_init(void) {
                                        TCCR1A = 0;
                                        TCCR1B = (1 << WGM12) | $csBits;
                                        OCR1A = ${ocr1a}U;
                                        TIMSK |= (1 << OCIE1A);
                                    }
                                    """.trimIndent() + "\n"
                                )
                            }
                        ) {
                            Text("+ Insert Timer1 ${selectedTimerMs}ms Init", color = OctoCyan)
                        }
                    } else {
                        Text(
                            "Period out of 16-bit Timer1 range for this F_CPU.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OctoWarning
                        )
                    }
                }
            }
        }
    }
}
