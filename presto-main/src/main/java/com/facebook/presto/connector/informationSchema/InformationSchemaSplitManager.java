/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.connector.informationSchema;

import com.facebook.presto.spi.ConnectorColumnHandle;
import com.facebook.presto.spi.ConnectorPartition;
import com.facebook.presto.spi.ConnectorPartitionResult;
import com.facebook.presto.spi.ConnectorSplit;
import com.facebook.presto.spi.ConnectorSplitManager;
import com.facebook.presto.spi.ConnectorSplitSource;
import com.facebook.presto.spi.ConnectorTableHandle;
import com.facebook.presto.spi.FixedSplitSource;
import com.facebook.presto.spi.HostAddress;
import com.facebook.presto.spi.NodeManager;
import com.facebook.presto.spi.TupleDomain;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

import javax.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class InformationSchemaSplitManager
        implements ConnectorSplitManager
{
    private final NodeManager nodeManager;

    @Inject
    public InformationSchemaSplitManager(NodeManager nodeManager)
    {
        this.nodeManager = checkNotNull(nodeManager, "nodeManager is null");
    }

    @Override
    public String getConnectorId()
    {
        // information schema is not a connector
        return null;
    }

    @Override
    public ConnectorPartitionResult getPartitions(ConnectorTableHandle table, TupleDomain<ConnectorColumnHandle> tupleDomain)
    {
        checkNotNull(table, "table is null");
        checkNotNull(tupleDomain, "tupleDomain is null");

        checkArgument(table instanceof InformationSchemaTableHandle, "TableHandle must be an InformationSchemaTableHandle");
        InformationSchemaTableHandle informationSchemaTableHandle = (InformationSchemaTableHandle) table;

        Map<ConnectorColumnHandle, Comparable<?>> bindings = tupleDomain.extractFixedValues();

        List<ConnectorPartition> partitions = ImmutableList.<ConnectorPartition>of(new InformationSchemaPartition(informationSchemaTableHandle, bindings));
        // We don't strip out the bindings that we have created from the undeterminedTupleDomain b/c the current InformationSchema
        // system requires that all filters be re-applied at execution time.
        return new ConnectorPartitionResult(partitions, tupleDomain);
    }

    @Override
    public ConnectorSplitSource getPartitionSplits(ConnectorTableHandle table, List<ConnectorPartition> partitions)
    {
        checkNotNull(partitions, "partitions is null");
        if (partitions.isEmpty()) {
            return new FixedSplitSource(null, ImmutableList.<ConnectorSplit>of());
        }

        ConnectorPartition partition = Iterables.getOnlyElement(partitions);
        checkArgument(partition instanceof InformationSchemaPartition, "Partition must be an informationSchema partition");
        InformationSchemaPartition informationSchemaPartition = (InformationSchemaPartition) partition;

        List<HostAddress> localAddress = ImmutableList.of(nodeManager.getCurrentNode().getHostAndPort());

        ImmutableMap.Builder<String, Object> filters = ImmutableMap.builder();
        for (Entry<ConnectorColumnHandle, Comparable<?>> entry : informationSchemaPartition.getFilters().entrySet()) {
            InformationSchemaColumnHandle informationSchemaColumnHandle = (InformationSchemaColumnHandle) entry.getKey();
            filters.put(informationSchemaColumnHandle.getColumnName(), entry.getValue());
        }

        ConnectorSplit split = new InformationSchemaSplit(informationSchemaPartition.table, filters.build(), localAddress);

        return new FixedSplitSource(null, ImmutableList.of(split));
    }

    public static class InformationSchemaPartition
            implements ConnectorPartition
    {
        private final InformationSchemaTableHandle table;
        private final Map<ConnectorColumnHandle, Comparable<?>> filters;

        public InformationSchemaPartition(InformationSchemaTableHandle table, Map<ConnectorColumnHandle, Comparable<?>> filters)
        {
            this.table = checkNotNull(table, "table is null");
            this.filters = ImmutableMap.copyOf(checkNotNull(filters, "filters is null"));
        }

        public InformationSchemaTableHandle getTable()
        {
            return table;
        }

        @Override
        public String getPartitionId()
        {
            return table.getSchemaTableName().toString();
        }

        @Override
        public TupleDomain<ConnectorColumnHandle> getTupleDomain()
        {
            return TupleDomain.withFixedValues(filters);
        }

        public Map<ConnectorColumnHandle, Comparable<?>> getFilters()
        {
            return filters;
        }

        @Override
        public String toString()
        {
            return Objects.toStringHelper(this)
                    .add("table", table)
                    .add("filters", filters)
                    .toString();
        }
    }
}
