/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.rest.action.admin.cluster;

import org.elasticsearch.action.admin.cluster.allocation.ClusterAllocationExplainRequest;
import org.elasticsearch.action.admin.cluster.allocation.TransportClusterAllocationExplainAction;
import org.elasticsearch.client.internal.node.NodeClient;
import org.elasticsearch.common.util.set.Sets;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestUtils;
import org.elasticsearch.rest.Scope;
import org.elasticsearch.rest.ServerlessScope;
import org.elasticsearch.rest.action.RestRefCountedChunkedToXContentListener;
import org.elasticsearch.xcontent.XContentParser;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.elasticsearch.rest.RestRequest.Method.GET;
import static org.elasticsearch.rest.RestRequest.Method.POST;

/**
 * Class handling cluster allocation explanation at the REST level
 */
@ServerlessScope(Scope.INTERNAL)
public class RestClusterAllocationExplainAction extends BaseRestHandler {

    @Override
    public List<Route> routes() {
        return List.of(new Route(GET, "/_cluster/allocation/explain"), new Route(POST, "/_cluster/allocation/explain"));
    }

    @Override
    public String getName() {
        return "cluster_allocation_explain_action";
    }

    @Override
    public boolean allowSystemIndexAccessByDefault() {
        return true;
    }

    @Override
    public Set<String> allSupportedParameters() {
        return ClusterAllocationExplainRequest.ALL_SUPPORTED_PARAMETERS;
    }

    @Override
    public Set<String> supportedQueryParameters() {
        return ClusterAllocationExplainRequest.QUERY_PARAMETERS;
    }

    @Override
    public RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) throws IOException {
        /*
            https://github.com/elastic/elasticsearch/issues/127028 introduces dual behaviour for this API.
            We now support either, but not a mix of:
            1. Parameters being passed in the URL
            2. The legacy behaviour of passing parameters in the body of the request
         */

//        boolean userPassedParametersInPath = !request.params().isEmpty();

        boolean userPassedParametersInPath = isPathParameterProvided(request.params().keySet());
        final var clusterAllocationExplainRequest = new ClusterAllocationExplainRequest(RestUtils.getMasterNodeTimeout(request));

        if (userPassedParametersInPath) {
            String index = request.param(ClusterAllocationExplainRequest.INDEX_PARAMETER_NAME);
            if (index == null) {
                throw new IllegalArgumentException("The index parameter cannot be blank.");
            }
            clusterAllocationExplainRequest.setIndex(index);

            String shard = request.param(ClusterAllocationExplainRequest.SHARD_PARAMETER_NAME);
            if (shard == null) {
                throw new IllegalArgumentException("The shard parameter cannot be blank.");
            }
            clusterAllocationExplainRequest.setShard(Integer.parseInt(shard));

            String primary = request.param(ClusterAllocationExplainRequest.PRIMARY_PARAMETER_NAME);
            if (primary == null) {
                throw new IllegalArgumentException("The primary parameter cannot be blank.");
            }
            clusterAllocationExplainRequest.setPrimary(Boolean.parseBoolean(primary));

            String current_node = request.param(ClusterAllocationExplainRequest.CURRENT_NODE_PARAMETER_NAME);
            if (current_node == null) {
                throw new IllegalArgumentException("The current_node parameter cannot be blank.");
            }
            clusterAllocationExplainRequest.setCurrentNode(current_node);

            // TODO - include_yes_decisions and include_disk_info
        } else {
            if (request.hasContentOrSourceParam()) {
                try (XContentParser parser = request.contentOrSourceParamParser()) {
                    ClusterAllocationExplainRequest.parse(clusterAllocationExplainRequest, parser);
                }
            } // else ok, an empty body means "explain the first unassigned shard you find"
            // TODO - Set all 4 params to being consumed

            // TODO - Can we move this outside the IF statement so it runs for both?
            clusterAllocationExplainRequest.includeYesDecisions(request.paramAsBoolean("include_yes_decisions", false));
            clusterAllocationExplainRequest.includeDiskInfo(request.paramAsBoolean("include_disk_info", false));
        }

        return channel -> client.execute(
            TransportClusterAllocationExplainAction.TYPE,
            clusterAllocationExplainRequest,
            new RestRefCountedChunkedToXContentListener<>(channel)
        );
    }

    private boolean isPathParameterProvided(Set<String> parameters) {
        for (String parameter : parameters) {
            if (ClusterAllocationExplainRequest.PATH_PARAMETERS.contains(parameter)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canTripCircuitBreaker() {
        return false;
    }
}
