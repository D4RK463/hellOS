// SPDX-License-Identifier: GPL-3.0-or-later
/**
 * hellOS: a Java library for the Denon/Marantz HEOS CLI protocol.
 */
module org.hellos {
    requires transitive tools.jackson.databind;

    exports org.hellos.cli;
    exports org.hellos.connection;
    exports org.hellos;
    exports org.hellos.blocking;
    exports org.hellos.discovery;
    exports org.hellos.event;
    exports org.hellos.model;
}
