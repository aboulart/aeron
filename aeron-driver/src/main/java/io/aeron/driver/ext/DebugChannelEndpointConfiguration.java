/*
 * Copyright 2016 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.aeron.driver.ext;

import static java.lang.Long.getLong;
import static java.lang.System.getProperty;

public class DebugChannelEndpointConfiguration
{
    /**
     * Property name for receiver inbound data loss rate.
     */
    public static final String RECEIVE_DATA_LOSS_RATE_PROP_NAME = "aeron.debug.receive.data.loss.rate";

    /**
     * Property name for receiver inbound data loss seed.
     */
    public static final String RECEIVE_DATA_LOSS_SEED_PROP_NAME = "aeron.debug.receive.data.loss.seed";

    /**
     * Property name for receiver outbound control loss rate.
     */
    public static final String RECEIVE_CONTROL_LOSS_RATE_PROP_NAME = "aeron.debug.receive.control.loss.rate";

    /**
     * Property name for receiver outbound control loss seed.
     */
    public static final String RECEIVE_CONTROL_LOSS_SEED_PROP_NAME = "aeron.debug.receive.control.loss.seed";

    /**
     * Property name for sender outbound data loss rate.
     */
    public static final String SEND_DATA_LOSS_RATE_PROP_NAME = "aeron.debug.send.data.loss.rate";

    /**
     * Property name for sender outbound data loss seed.
     */
    public static final String SEND_DATA_LOSS_SEED_PROP_NAME = "aeron.debug.send.data.loss.seed";

    /**
     * Property name for sender inbound control loss rate.
     */
    public static final String SEND_CONTROL_LOSS_RATE_PROP_NAME = "aeron.debug.send.control.loss.rate";

    /**
     * Property name for sender inbound control loss seed.
     */
    public static final String SEND_CONTROL_LOSS_SEED_PROP_NAME = "aeron.debug.send.control.loss.seed";

    public static final long RECEIVE_DATA_LOSS_SEED = getLong(RECEIVE_DATA_LOSS_SEED_PROP_NAME, -1);

    public static final double RECEIVE_DATA_LOSS_RATE =
        Double.parseDouble(getProperty(RECEIVE_DATA_LOSS_RATE_PROP_NAME, "0.0"));

    public static final long RECEIVE_CONTROL_LOSS_SEED = getLong(RECEIVE_CONTROL_LOSS_SEED_PROP_NAME, -1);

    public static final double RECEIVE_CONTROL_LOSS_RATE =
        Double.parseDouble(getProperty(RECEIVE_CONTROL_LOSS_RATE_PROP_NAME, "0.0"));

    public static final long SEND_DATA_LOSS_SEED = getLong(SEND_DATA_LOSS_SEED_PROP_NAME, -1);

    public static final double SEND_DATA_LOSS_RATE =
        Double.parseDouble(getProperty(SEND_DATA_LOSS_RATE_PROP_NAME, "0.0"));

    public static final long SEND_CONTROL_LOSS_SEED = getLong(SEND_CONTROL_LOSS_SEED_PROP_NAME, -1);

    public static final double SEND_CONTROL_LOSS_RATE =
        Double.parseDouble(getProperty(SEND_CONTROL_LOSS_RATE_PROP_NAME, "0.0"));

    public static LossGenerator lossGeneratorSupplier(final double lossRate, final long lossSeed)
    {
        if (0 == lossRate)
        {
            return (address, buffer, length) -> false;
        }

        return new RandomLossGenerator(lossRate, lossSeed);
    }

    public static LossGenerator receiveDataLossGeneratorSupplier()
    {
        return lossGeneratorSupplier(RECEIVE_DATA_LOSS_RATE, RECEIVE_DATA_LOSS_SEED);
    }

    public static LossGenerator receiveControlLossGeneratorSupplier()
    {
        return lossGeneratorSupplier(RECEIVE_CONTROL_LOSS_RATE, RECEIVE_CONTROL_LOSS_SEED);
    }

    public static LossGenerator sendDataLossGeneratorSupplier()
    {
        return lossGeneratorSupplier(SEND_DATA_LOSS_RATE, SEND_DATA_LOSS_SEED);
    }

    public static LossGenerator sendControlLossGeneratorSupplier()
    {
        return lossGeneratorSupplier(SEND_CONTROL_LOSS_RATE, SEND_CONTROL_LOSS_SEED);
    }
}