/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *   WSO2 Inc. licenses this file to you under the Apache License,
 *   Version 2.0 (the "License"); you may not use this file except
 *   in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 * /
 */

package org.wso2.is.key.manager.core.internal;

import org.wso2.carbon.registry.core.service.RegistryService;
import org.wso2.carbon.registry.core.service.TenantRegistryLoader;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.carbon.utils.ConfigurationContextService;

/**
 * Service holder to keep track on osgi Services
 */
public class ServiceReferenceHolder {

    private static final ServiceReferenceHolder instance = new ServiceReferenceHolder();
    private RealmService realmService;
    private RegistryService registryService;
    private TenantRegistryLoader tenantRegistryLoader;
    private static ConfigurationContextService contextService;
    private static boolean restrictUnassignedScopes;
    private static boolean restrictApimRestApiScopes;
    private static boolean mergeApplicationScopes;

    private ServiceReferenceHolder() {

    }

    public static ServiceReferenceHolder getInstance() {

        return instance;
    }

    public void setRealmService(RealmService realmService) {

        this.realmService = realmService;
    }

    public RealmService getRealmService() {

        return realmService;
    }

    public RegistryService getRegistryService() {
        return registryService;
    }

    public void setRegistryService(RegistryService registryService) {
        this.registryService = registryService;
    }

    public TenantRegistryLoader getTenantRegistryLoader() {
        return tenantRegistryLoader;
    }

    public void setTenantRegistryLoader(TenantRegistryLoader tenantRegistryLoader) {
        this.tenantRegistryLoader = tenantRegistryLoader;
    }

    public static ConfigurationContextService getContextService() {
        return contextService;
    }

    public static void setContextService(ConfigurationContextService contextService) {
        ServiceReferenceHolder.contextService = contextService;
    }

    public static boolean isRestrictUnassignedScopes() {
        return restrictUnassignedScopes;
    }

    public static void setRestrictUnassignedScopes(boolean restrictUnassignedScopes) {
        ServiceReferenceHolder.restrictUnassignedScopes = restrictUnassignedScopes;
    }

    public static boolean isRestrictApimRestApiScopes() {
        return restrictApimRestApiScopes;
    }

    public static void setRestrictApimRestApiScopes(boolean restrictProductRestApiScopes) {
        ServiceReferenceHolder.restrictApimRestApiScopes = restrictProductRestApiScopes;
    }

    public static boolean isMergeApplicationScopes() {
        return mergeApplicationScopes;
    }

    public static void setMergeApplicationScopes(boolean mergeApplicationScopes) {
        ServiceReferenceHolder.mergeApplicationScopes = mergeApplicationScopes;
    }
}
