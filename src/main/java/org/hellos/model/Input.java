// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.model;

import java.util.Optional;

/**
 * Input sources for {@code browse/play_input} (spec section 4.4.9 plus the generic
 * {@code inputs/sourceN} names of newer AVR firmware). Which inputs exist depends on the device.
 * Methods that take an input also accept the raw name as string, for inputs not listed here.
 */
public enum Input {
    AUX_IN_1("inputs/aux_in_1"),
    AUX_IN_2("inputs/aux_in_2"),
    AUX_IN_3("inputs/aux_in_3"),
    AUX_IN_4("inputs/aux_in_4"),
    AUX_SINGLE("inputs/aux_single"),
    AUX1("inputs/aux1"),
    AUX2("inputs/aux2"),
    AUX3("inputs/aux3"),
    AUX4("inputs/aux4"),
    AUX5("inputs/aux5"),
    AUX6("inputs/aux6"),
    AUX7("inputs/aux7"),
    AUX_8K("inputs/aux_8k"),
    LINE_IN_1("inputs/line_in_1"),
    LINE_IN_2("inputs/line_in_2"),
    LINE_IN_3("inputs/line_in_3"),
    LINE_IN_4("inputs/line_in_4"),
    COAX_IN_1("inputs/coax_in_1"),
    COAX_IN_2("inputs/coax_in_2"),
    OPTICAL_IN_1("inputs/optical_in_1"),
    OPTICAL_IN_2("inputs/optical_in_2"),
    OPTICAL_IN_3("inputs/optical_in_3"),
    HDMI_IN_1("inputs/hdmi_in_1"),
    HDMI_IN_2("inputs/hdmi_in_2"),
    HDMI_IN_3("inputs/hdmi_in_3"),
    HDMI_IN_4("inputs/hdmi_in_4"),
    HDMI_ARC_1("inputs/hdmi_arc_1"),
    CABLE_SAT("inputs/cable_sat"),
    DVD("inputs/dvd"),
    BLURAY("inputs/bluray"),
    GAME("inputs/game"),
    GAME2("inputs/game2"),
    MEDIA_PLAYER("inputs/mediaplayer"),
    CD("inputs/cd"),
    TUNER("inputs/tuner"),
    HD_RADIO("inputs/hdradio"),
    TV_AUDIO("inputs/tvaudio"),
    TV("inputs/tv"),
    PHONO("inputs/phono"),
    USB_DAC("inputs/usbdac"),
    ANALOG_IN_1("inputs/analog_in_1"),
    ANALOG_IN_2("inputs/analog_in_2"),
    RECORDER_IN_1("inputs/recorder_in_1"),
    SOURCE_1("inputs/source1"),
    SOURCE_2("inputs/source2"),
    SOURCE_3("inputs/source3"),
    SOURCE_4("inputs/source4"),
    SOURCE_5("inputs/source5"),
    SOURCE_6("inputs/source6"),
    SOURCE_7("inputs/source7"),
    SOURCE_8("inputs/source8"),
    SOURCE_9("inputs/source9"),
    SOURCE_10("inputs/source10"),
    SOURCE_11("inputs/source11"),
    SOURCE_12("inputs/source12"),
    SOURCE_13("inputs/source13"),
    SOURCE_14("inputs/source14"),
    SOURCE_15("inputs/source15"),
    SOURCE_16("inputs/source16"),
    SOURCE_17("inputs/source17"),
    SOURCE_18("inputs/source18");

    /** Prefix of all input names. */
    public static final String PREFIX = "inputs/";

    private final String wireValue;

    Input(String wireValue) {
        this.wireValue = wireValue;
    }

    /** The name used in the protocol, e.g. {@code inputs/aux_in_1}. */
    public String wireValue() {
        return wireValue;
    }

    public static Optional<Input> fromWire(String value) {
        for (Input input : values()) {
            if (input.wireValue.equals(value)) {
                return Optional.of(input);
            }
        }
        return Optional.empty();
    }
}
