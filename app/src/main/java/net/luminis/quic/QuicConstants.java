/*
 * Copyright © 2019, 2020 Peter Doornbosch
 *
 * This file is part of Kwik, a QUIC client Java library
 *
 * Kwik is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Kwik is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package net.luminis.quic;


public class QuicConstants {

    // https://tools.ietf.org/html/draft-ietf-quic-transport-17#section-18
    public enum TransportParameterId {
        original_destination_connection_id (0),
        idle_timeout(1),
        stateless_reset_token(2),
        max_packet_size(3),
        initial_max_data(4),
        initial_max_stream_data_bidi_local(5),
        initial_max_stream_data_bidi_remote(6),
        initial_max_stream_data_uni(7),
        initial_max_streams_bidi(8),
        initial_max_streams_uni(9),
        ack_delay_exponent(0x0a),
        max_ack_delay(0x0b),
        disable_active_migration(0x0c),
        preferred_address(0x0d),
        active_connection_id_limit(0x0e),
        initial_source_connection_id(0x0f),
        retry_source_connection_id(0x10)
        // (65535)
        ;
        public final short value;

        TransportParameterId(int value) {
            this.value = (short) value;
        }
    }


    // https://tools.ietf.org/html/draft-ietf-quic-transport-16#section-18
    public enum TransportParameterIdPreDraft17 {
        initial_max_stream_data_bidi_local(0),
        initial_max_data(1),
        initial_max_bidi_streams(2),
        idle_timeout(3),
        preferred_address(4),
        max_packet_size(5),
        stateless_reset_token(6),
        ack_delay_exponent(7),
        initial_max_uni_streams(8),
        disable_migration(9),
        initial_max_stream_data_bidi_remote(10),
        initial_max_stream_data_uni(11),
        max_ack_delay(12),
        original_connection_id(13),
        // (65535)
        ;
        public final short value;

        TransportParameterIdPreDraft17(int value) {
            this.value = (short) value;
        }
    }

    public enum IpVersion {
        IPv4(4),
        IPv6(6),
        // (15)
        ;
        public final byte value;

        IpVersion(int value) {
            this.value = (byte) value;
        }
    }

    // https://tools.ietf.org/html/draft-ietf-quic-transport-18#section-20
    public enum TransportErrorCode {
        NO_ERROR (0x0),
        INTERNAL_ERROR (0x1),
        SERVER_BUSY (0x2),
        FLOW_CONTROL_ERROR (0x3),
        STREAM_LIMIT_ERROR (0x4),
        STREAM_STATE_ERROR (0x5),
        FINAL_SIZE_ERROR (0x6),
        FRAME_ENCODING_ERROR (0x7),
        TRANSPORT_PARAMETER_ERROR (0x8),
        VERSION_NEGOTIATION_ERROR (0x9),
        PROTOCOL_VIOLATION (0xA),
        INVALID_MIGRATION (0xC),
        CRYPTO_ERROR (0x100),
        ;

        private final short value;

        TransportErrorCode(int value) {
            this.value = (short) value;
        }
    }
}
