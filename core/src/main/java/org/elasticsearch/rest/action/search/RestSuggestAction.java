/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.rest.action.search;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestResponse;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.rest.action.RestActions;
import org.elasticsearch.rest.action.RestBuilderListener;
import org.elasticsearch.search.SearchRequestParsers;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.suggest.Suggest;
import org.elasticsearch.search.suggest.SuggestBuilder;

import java.io.IOException;

import static org.elasticsearch.rest.RestRequest.Method.GET;
import static org.elasticsearch.rest.RestRequest.Method.POST;
import static org.elasticsearch.rest.action.RestActions.buildBroadcastShardsHeader;

public class RestSuggestAction extends BaseRestHandler {

    private final SearchRequestParsers searchRequestParsers;

    @Inject
    public RestSuggestAction(Settings settings, RestController controller,
                             SearchRequestParsers searchRequestParsers) {
        super(settings);
        this.searchRequestParsers = searchRequestParsers;
        controller.registerHandler(POST, "/_suggest", this);
        controller.registerHandler(GET, "/_suggest", this);
        controller.registerHandler(POST, "/{index}/_suggest", this);
        controller.registerHandler(GET, "/{index}/_suggest", this);
    }

    @Override
    public RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) throws IOException {
        final SearchRequest searchRequest = new SearchRequest(
                Strings.splitStringByCommaToArray(request.param("index")), new SearchSourceBuilder());
        searchRequest.indicesOptions(IndicesOptions.fromRequest(request, searchRequest.indicesOptions()));
        if (RestActions.hasBodyContent(request)) {
            final BytesReference sourceBytes = RestActions.getRestContent(request);
            try (XContentParser parser = XContentFactory.xContent(sourceBytes).createParser(sourceBytes)) {
                final QueryParseContext context = new QueryParseContext(searchRequestParsers.queryParsers, parser, parseFieldMatcher);
                searchRequest.source().suggest(SuggestBuilder.fromXContent(context, searchRequestParsers.suggesters));
            }
        } else {
            throw new IllegalArgumentException("no content or source provided to execute suggestion");
        }
        searchRequest.routing(request.param("routing"));
        searchRequest.preference(request.param("preference"));
        return channel -> client.search(searchRequest, new RestBuilderListener<SearchResponse>(channel) {
            @Override
            public RestResponse buildResponse(SearchResponse response, XContentBuilder builder) throws Exception {
                RestStatus restStatus = RestStatus.status(response.getSuccessfulShards(),
                    response.getTotalShards(), response.getShardFailures());
                builder.startObject();
                buildBroadcastShardsHeader(builder, request, response.getTotalShards(),
                    response.getSuccessfulShards(), response.getFailedShards(), response.getShardFailures());
                Suggest suggest = response.getSuggest();
                if (suggest != null) {
                    suggest.toInnerXContent(builder, request);
                }
                builder.endObject();
                return new BytesRestResponse(restStatus, builder);
            }
        });
    }
}
