/*
 * Copyright 2026 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
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
package org.keycloak.protocol.oidc.mappers;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.keycloak.models.GroupModel;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.ProtocolMapperUtils;
import org.keycloak.representations.IDToken;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GroupMembershipMapperTest {

    @Test
    public void setClaimDoesNotModifyTheMappingModel() {
        Map<String, String> config = new HashMap<>();
        config.put(OIDCAttributeMapperHelper.TOKEN_CLAIM_NAME, "groups");
        config.put("full.path", "false");
        config.put(OIDCAttributeMapperHelper.INCLUDE_IN_ID_TOKEN, "true");
        ProtocolMapperModel mappingModel = new ProtocolMapperModel();
        mappingModel.setConfig(config);
        Map<String, String> configBefore = new HashMap<>(config);

        IDToken token = new IDToken();
        new GroupMembershipMapper().setClaim(token, mappingModel, userSession("a", "b"));

        // the mapping model is the cached one of the client, shared by every request
        assertEquals(configBefore, mappingModel.getConfig());
        assertEquals(List.of("a", "b"), token.getOtherClaims().get("groups"));
    }

    @Test
    public void setClaimForcesMultivalued() {
        Map<String, String> config = new HashMap<>();
        config.put(OIDCAttributeMapperHelper.TOKEN_CLAIM_NAME, "groups");
        config.put("full.path", "false");
        config.put(OIDCAttributeMapperHelper.INCLUDE_IN_ID_TOKEN, "true");
        config.put(ProtocolMapperUtils.MULTIVALUED, "false");
        ProtocolMapperModel mappingModel = new ProtocolMapperModel();
        mappingModel.setConfig(config);

        IDToken token = new IDToken();
        new GroupMembershipMapper().setClaim(token, mappingModel, userSession("a"));

        // a single group is still a list in the token, whatever the configuration says
        assertEquals(List.of("a"), token.getOtherClaims().get("groups"));
        assertEquals("false", mappingModel.getConfig().get(ProtocolMapperUtils.MULTIVALUED));
    }

    private UserSessionModel userSession(String... groupNames) {
        UserModel user = proxy(UserModel.class, (proxy, method, args) -> {
            if ("getGroupsStream".equals(method.getName())) {
                return Stream.of(groupNames).map(this::group);
            }
            throw new UnsupportedOperationException(method.getName());
        });
        return proxy(UserSessionModel.class, (proxy, method, args) -> {
            if ("getUser".equals(method.getName())) {
                return user;
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }

    private GroupModel group(String name) {
        return proxy(GroupModel.class, (proxy, method, args) -> {
            if ("getName".equals(method.getName())) {
                return name;
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }

    private <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
