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
 *
 */
package org.wso2.is.key.manager.core.tokenmgt.issuers;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.apache.axis2.util.JavaUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.opensaml.saml.saml2.core.Assertion;
import org.wso2.carbon.identity.application.authentication.framework.model.AuthenticatedUser;
import org.wso2.carbon.identity.application.authentication.framework.util.FrameworkUtils;
import org.wso2.carbon.identity.application.common.model.ClaimMapping;
import org.wso2.carbon.identity.application.common.model.FederatedAuthenticatorConfig;
import org.wso2.carbon.identity.application.common.model.IdentityProvider;
import org.wso2.carbon.identity.application.common.model.PermissionsAndRoleConfig;
import org.wso2.carbon.identity.application.common.model.RoleMapping;
import org.wso2.carbon.identity.application.common.model.ServiceProvider;
import org.wso2.carbon.identity.application.common.model.ServiceProviderProperty;
import org.wso2.carbon.identity.application.common.util.IdentityApplicationConstants;
import org.wso2.carbon.identity.application.common.util.IdentityApplicationManagementUtil;
import org.wso2.carbon.identity.base.IdentityConstants;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.identity.oauth.OAuthUtil;
import org.wso2.carbon.identity.oauth.cache.CacheEntry;
import org.wso2.carbon.identity.oauth.cache.OAuthCache;
import org.wso2.carbon.identity.oauth.cache.OAuthCacheKey;
import org.wso2.carbon.identity.oauth.callback.OAuthCallback;
import org.wso2.carbon.identity.oauth.common.GrantType;
import org.wso2.carbon.identity.oauth.common.OAuthConstants;
import org.wso2.carbon.identity.oauth.common.exception.InvalidOAuthClientException;
import org.wso2.carbon.identity.oauth.config.OAuthServerConfiguration;
import org.wso2.carbon.identity.oauth2.IdentityOAuth2Exception;
import org.wso2.carbon.identity.oauth2.IdentityOAuth2ScopeServerException;
import org.wso2.carbon.identity.oauth2.Oauth2ScopeConstants;
import org.wso2.carbon.identity.oauth2.authz.OAuthAuthzReqMessageContext;
import org.wso2.carbon.identity.oauth2.bean.Scope;
import org.wso2.carbon.identity.oauth2.dao.OAuthTokenPersistenceFactory;
import org.wso2.carbon.identity.oauth2.dto.OAuth2TokenValidationRequestDTO;
import org.wso2.carbon.identity.oauth2.grant.jwt.JWTConstants;
import org.wso2.carbon.identity.oauth2.model.AccessTokenDO;
import org.wso2.carbon.identity.oauth2.model.RequestParameter;
import org.wso2.carbon.identity.oauth2.model.ResourceScopeCacheEntry;
import org.wso2.carbon.identity.oauth2.token.OAuthTokenReqMessageContext;
import org.wso2.carbon.identity.oauth2.util.OAuth2Util;
import org.wso2.carbon.identity.oauth2.validators.OAuth2TokenValidationMessageContext;
import org.wso2.carbon.identity.oauth2.validators.scope.ScopeValidator;
import org.wso2.carbon.idp.mgt.IdentityProviderManagementException;
import org.wso2.carbon.idp.mgt.IdentityProviderManager;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.api.UserStoreManager;
import org.wso2.carbon.user.core.UserCoreConstants;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.carbon.user.core.util.UserCoreUtil;
import org.wso2.carbon.utils.multitenancy.MultitenantUtils;
import org.wso2.is.key.manager.core.internal.ServiceReferenceHolder;
import org.wso2.is.key.manager.core.tokenmgt.handlers.ResourceConstants;
import org.wso2.is.key.manager.core.tokenmgt.util.TokenMgtUtil;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.wso2.carbon.registry.core.jdbc.DumpConstants.RESOURCE;

/**
 * This class implements Scope Validator which represents the functions related to an scope issuer which
 * issues scopes based on user roles.
 */
public class RoleBasedScopesIssuer extends AbstractScopesIssuer implements ScopeValidator {

    private static Log log = LogFactory.getLog(RoleBasedScopesIssuer.class);
    private static final String DEFAULT_SCOPE_NAME = "default";
    private static final String PRESERVED_CASE_SENSITIVE_VARIABLE = "preservedCaseSensitive";
    private static final String ACCESS_TOKEN_DO = "AccessTokenDO";
    // The following constants are as same as the constants defined in
    // org.wso2.carbon.apimgt.keymgt.handlers.ResourceConstants.
    // If any changes are taking place in that these should also be updated accordingly.
    // Setting the "retrieveRolesFromUserStoreForScopeValidation" as a System property which is used when
    // skipping the scope role validation during token issuing using JWT bearer grant.
    public static final String CHECK_ROLES_FROM_SAML_ASSERTION = "checkRolesFromSamlAssertion";
    public static final String RETRIEVE_ROLES_FROM_USERSTORE_FOR_SCOPE_VALIDATION =
            "retrieveRolesFromUserStoreForScopeValidation";
    private static final String SCOPE_VALIDATOR_NAME = "Role based scope validator";
    private static final String OPENID = "openid";
    protected boolean rejectUnregisteredScopes = false;
    private IdentityProvider identityProvider = null;
    // set role based scopes issuer as the default
    private static final String ISSUER_PREFIX = "default";
    OAuthServerConfiguration oAuthServerConfiguration = OAuthServerConfiguration.getInstance();
    private static final String REFRESH_TOKEN_GRANT_TYPE = "refresh_token";
    private static final String CLIENT_CREDENTIALS_GRANT_TYPE = "client_credentials";
    private static final String APPLICATION_SCOPES_PREFIX = "app_scopes_";

    @Override
    public boolean validateScope(OAuthAuthzReqMessageContext oAuthAuthzReqMessageContext) throws
            IdentityOAuth2Exception {

        List<String> authScopes = getScopes(oAuthAuthzReqMessageContext);
        oAuthAuthzReqMessageContext.setApprovedScope(authScopes.toArray(new String[authScopes.size()]));
        return true;
    }

    @Override
    public boolean validateScope(OAuthTokenReqMessageContext oAuthTokenReqMessageContext) throws
            IdentityOAuth2Exception {

        String grantType = oAuthTokenReqMessageContext.getOauth2AccessTokenReqDTO().getGrantType();
        boolean isRefreshRequest = OAuthConstants.GrantTypes.REFRESH_TOKEN.equals(grantType);
        boolean isFederatedUser = oAuthTokenReqMessageContext.getAuthorizedUser().isFederatedUser();
        if (isRefreshRequest && isFederatedUser) {
            return true;
        }

        List<String> authScopes = getScopes(oAuthTokenReqMessageContext);
        oAuthTokenReqMessageContext.setScope(authScopes.toArray(new String[authScopes.size()]));
        return true;
    }

    @Override
    public boolean validateScope(OAuth2TokenValidationMessageContext oAuth2TokenValidationMessageContext) throws
            IdentityOAuth2Exception {

        AccessTokenDO accessTokenDO = (AccessTokenDO) oAuth2TokenValidationMessageContext.getProperty(ACCESS_TOKEN_DO);
        if (accessTokenDO == null) {
            return false;
        }
        String resource = getResourceFromMessageContext(oAuth2TokenValidationMessageContext);
        // Return true if there is no resource to validate the token against.
        if (resource == null) {
            return true;
        }
        //Get the list of scopes associated with the access token
        String[] scopes = accessTokenDO.getScope();

        //If no scopes are associated with the token
        if (scopes == null || scopes.length == 0) {
            return true;
        }

        String resourceScope = null;
        int resourceTenantId = -1;

        boolean cacheHit = false;
        // Check the cache, if caching is enabled.
        OAuthCacheKey cacheKey = new OAuthCacheKey(resource);
        CacheEntry result = OAuthCache.getInstance().getValueFromCache(cacheKey);

        //Cache hit
        if (result != null && result instanceof ResourceScopeCacheEntry) {
            resourceScope = ((ResourceScopeCacheEntry) result).getScope();
            resourceTenantId = ((ResourceScopeCacheEntry) result).getTenantId();
            cacheHit = true;
        }

        // Cache was not hit. So retrieve from database.
        if (!cacheHit) {
            Pair<String, Integer> scopeMap = OAuthTokenPersistenceFactory.getInstance()
                    .getTokenManagementDAO().findTenantAndScopeOfResource(resource);

            if (scopeMap != null) {
                resourceScope = scopeMap.getLeft();
                resourceTenantId = scopeMap.getRight();
            }

            cacheKey = new OAuthCacheKey(resource);
            ResourceScopeCacheEntry cacheEntry = new ResourceScopeCacheEntry(resourceScope);
            cacheEntry.setTenantId(resourceTenantId);
            //Store resourceScope in cache even if it is null (to avoid database calls when accessing resources for
            //which scopes haven't been defined).
            OAuthCache.getInstance().addToCache(cacheKey, cacheEntry);
        }

        //Return TRUE if - There does not exist a scope definition for the resource
        if (resourceScope == null) {
            if (log.isDebugEnabled()) {
                log.debug("Resource '" + resource + "' is not protected with a scope");
            }
            return true;
        }

        List<String> scopeList = new ArrayList<>(Arrays.asList(scopes));

        // If the access token does not bear the scope required for accessing the Resource.
        if (!scopeList.contains(resourceScope)) {
            if (log.isDebugEnabled() && IdentityUtil.isTokenLoggable(IdentityConstants.IdentityTokens.ACCESS_TOKEN)) {
                log.debug("Access token '" + accessTokenDO.getAccessToken() + "' does not bear the scope '" +
                        resourceScope + "'");
            }
            return false;
        }

        // If a federated user and CHECK_ROLES_FROM_SAML_ASSERTION system property is set to true,
        // or if a federated user and RETRIEVE_ROLES_FROM_USERSTORE_FOR_SCOPE_VALIDATION system property is false,
        // avoid validating user roles.
        // This system property is set at server start using -D option, Thus will be a permanent property.
        if (accessTokenDO.getAuthzUser().isFederatedUser()
                && (Boolean.parseBoolean(System.getProperty(CHECK_ROLES_FROM_SAML_ASSERTION)) ||
                !(Boolean.parseBoolean(System.getProperty(RETRIEVE_ROLES_FROM_USERSTORE_FOR_SCOPE_VALIDATION))))) {
            return true;
        }

        AuthenticatedUser authenticatedUser = OAuthUtil
                .getAuthenticatedUser(oAuth2TokenValidationMessageContext.getResponseDTO().getAuthorizedUser());
        String clientId = accessTokenDO.getConsumerKey();
        List<String> requestedScopes = Arrays.asList(scopes);
        List<String> authorizedScopes = null;

        String[] userRoles = null;
        Map<String, String> appScopes = getAppScopes(clientId, authenticatedUser, requestedScopes);
        if (appScopes != null) {
            //If no scopes can be found in the context of the application
            if (isAppScopesEmpty(appScopes, clientId)) {
                authorizedScopes = getAllowedScopes(requestedScopes);
                oAuth2TokenValidationMessageContext.getResponseDTO().setScope(authorizedScopes.toArray(
                        new String[authorizedScopes.size()]));
                return true;
            }
            userRoles = getUserRoles(authenticatedUser, null);
            authorizedScopes = getAuthorizedScopes(userRoles, requestedScopes, appScopes);
            oAuth2TokenValidationMessageContext.getResponseDTO().setScope(authorizedScopes.toArray(
                    new String[authorizedScopes.size()]));
        }
        if (ArrayUtils.isEmpty(userRoles)) {
            if (log.isDebugEnabled()) {
                log.debug("No roles associated for the user " + authenticatedUser.getUserName());
            }
            return false;
        }
        return true;
    }

    protected Set<Scope> retrieveScopes(int tenantId) throws IdentityOAuth2ScopeServerException {

        return OAuthTokenPersistenceFactory.getInstance().getOAuthScopeDAO().getScopes(tenantId,
                Oauth2ScopeConstants.DEFAULT_SCOPE_BINDING);
    }

    @Override
    public String getName() {

        return SCOPE_VALIDATOR_NAME;
    }

    @Override
    public String getPrefix() {

        return ISSUER_PREFIX;
    }

    /**
     * Extract the resource from the access token validation request message
     *
     * @param messageContext Message context of the token validation request
     * @return resource
     */
    private String getResourceFromMessageContext(OAuth2TokenValidationMessageContext messageContext) {

        String resource = null;
        if (messageContext.getRequestDTO().getContext() != null) {
            // Iterate the array of context params to find the 'resource' context param.
            for (OAuth2TokenValidationRequestDTO.TokenValidationContextParam resourceParam :
                    messageContext.getRequestDTO().getContext()) {
                // If the context param is the resource that is being accessed
                if (resourceParam != null && RESOURCE.equals(resourceParam.getKey())) {
                    resource = resourceParam.getValue();
                    break;
                }
            }
        }
        return resource;
    }

    public List<String> getScopes(OAuthAuthzReqMessageContext oAuthAuthzReqMessageContext) {

        List<String> authorizedScopes;
        List<String> requestedScopes = null;
        List<String> scopes = new ArrayList<>();
        if (oAuthAuthzReqMessageContext.getApprovedScope() != null) {
            requestedScopes = new ArrayList<>(Arrays.asList(oAuthAuthzReqMessageContext.getApprovedScope()));
            boolean isRestrictApimRestApiScopes = ServiceReferenceHolder.isRestrictApimRestApiScopes();
            boolean isRestrictUnassignedScopes = ServiceReferenceHolder.isRestrictUnassignedScopes();
            if (!(isRestrictUnassignedScopes && isRestrictApimRestApiScopes)) {
                for (String scope : requestedScopes) {
                    // If requestedScopes contains Product REST APIs (Publisher/DevPortal/Admin) scopes, just let
                    // them pass to the final scope list returned from RoleBasedScopeIssuer. This is because
                    // RoleBasedScopeIssuer is not responsible for validating Product REST API scopes. Those will be
                    // handled by the SystemScopeIssuer.
                    if (checkForProductRestAPIScopes(scope)) {
                        scopes.add(scope);
                    }
                }
            }
            requestedScopes.removeAll(scopes);
            if (requestedScopes.isEmpty()) {
                return getAllowedScopes(scopes);
            }
        }
        String clientId = oAuthAuthzReqMessageContext.getAuthorizationReqDTO().getConsumerKey();
        AuthenticatedUser authenticatedUser = oAuthAuthzReqMessageContext.getAuthorizationReqDTO().getUser();
        Map<String, String> appScopes = getAppScopes(clientId, authenticatedUser, requestedScopes);
        if (appScopes != null) {
            //If no scopes can be found in the context of the application
            if (isAppScopesEmpty(appScopes, clientId)) {
                authorizedScopes = getAllowedScopes(requestedScopes);
                scopes.addAll(authorizedScopes);
                return scopes;
            }
            String[] userRoles = getUserRoles(authenticatedUser, null);
            authorizedScopes = getAuthorizedScopes(userRoles, requestedScopes, appScopes);
            scopes.addAll(authorizedScopes);
        }
        return scopes;
    }

    /**
     * This method is used to retrieve authorized scopes with respect to an authorization callback.
     *
     * @param scopeValidationCallback Authorization callback to validate scopes
     * @return authorized scopes list
     */
    @Override
    public List<String> getScopes(OAuthCallback scopeValidationCallback) {

        List<String> authorizedScopes = null;
        List<String> requestedScopes = Arrays.asList(scopeValidationCallback.getRequestedScope());
        String clientId = scopeValidationCallback.getClient();
        AuthenticatedUser authenticatedUser = scopeValidationCallback.getResourceOwner();

        Map<String, String> appScopes = getAppScopes(clientId, authenticatedUser, requestedScopes);
        if (appScopes != null) {
            //If no scopes can be found in the context of the application
            if (isAppScopesEmpty(appScopes, clientId)) {
                return getAllowedScopes(requestedScopes);
            }
            String[] userRoles = getUserRoles(authenticatedUser, null);
            authorizedScopes = getAuthorizedScopes(userRoles, requestedScopes, appScopes);
        }
        return authorizedScopes;
    }

    /**
     * This method is used to retrieve the authorized scopes with respect to a token.
     *
     * @param tokReqMsgCtx token message context
     * @return authorized scopes list
     */
    @Override
    public List<String> getScopes(OAuthTokenReqMessageContext tokReqMsgCtx) {

        List<String> authorizedScopes = new ArrayList<>();
        List<String> scopes = new ArrayList<>();
        List<String> requestedScopes = new ArrayList<>(Arrays.asList(tokReqMsgCtx.getScope()));
        boolean isRestrictApimRestApiScopes = ServiceReferenceHolder.isRestrictApimRestApiScopes();
        boolean isRestrictUnassignedScopes = ServiceReferenceHolder.isRestrictUnassignedScopes();
        if (!(isRestrictUnassignedScopes && isRestrictApimRestApiScopes)) {
            for (String scope : requestedScopes) {
                // If requestedScopes contains Product REST APIs (Publisher/DevPortal/Admin) scopes, just let
                // them pass to the final scope list returned from RoleBasedScopeIssuer. This is because
                // RoleBasedScopeIssuer is not responsible for validating Product REST API scopes. Those will be
                // handled by SystemScopeIssuer.
                if (checkForProductRestAPIScopes(scope)) {
                    scopes.add(scope);
                }
            }
        }
        requestedScopes.removeAll(scopes);
        String clientId = tokReqMsgCtx.getOauth2AccessTokenReqDTO().getClientId();
        AuthenticatedUser authenticatedUser = tokReqMsgCtx.getAuthorizedUser();
        String grantType = tokReqMsgCtx.getOauth2AccessTokenReqDTO().getGrantType();
        if (requestedScopes.isEmpty()) {
            return getAllowedScopes(scopes);
        }
        Map<String, String> appScopes = getAppScopes(clientId, authenticatedUser, requestedScopes);
        if (appScopes != null) {
            String[] userRoles = new String[0];
            // If no scopes can be found in the context of the application
            if (isAppScopesEmpty(appScopes, clientId) && !StringUtils.equals(CLIENT_CREDENTIALS_GRANT_TYPE,
                    grantType)) {
                authorizedScopes = getAuthorizedScopes(userRoles, requestedScopes, appScopes);
                scopes.addAll(authorizedScopes);
                return scopes;
            }

            // If GrantType is SAML20_BEARER and CHECK_ROLES_FROM_SAML_ASSERTION is true, or if GrantType is
            // JWT_BEARER and retrieveRolesFromUserStoreForScopeValidation system property is true,
            // use user roles from assertion or jwt otherwise use roles from userstore.
            String isSAML2Enabled = System.getProperty(ResourceConstants.CHECK_ROLES_FROM_SAML_ASSERTION);
            String isRetrieveRolesFromUserStoreForScopeValidation = System
                    .getProperty(ResourceConstants.RETRIEVE_ROLES_FROM_USERSTORE_FOR_SCOPE_VALIDATION);
            if (ResourceConstants.TOKEN_EXCHANGE_GRANT.equals(grantType)) {
                configureForJWTGrantOrExchangeGrant(tokReqMsgCtx, true);
                Map<ClaimMapping, String> userAttributes = authenticatedUser.getUserAttributes();
                if (tokReqMsgCtx.getProperty(ResourceConstants.ROLE_CLAIM) != null) {
                    userRoles = getRolesFromUserAttribute(userAttributes,
                            tokReqMsgCtx.getProperty(ResourceConstants.ROLE_CLAIM).toString());
                }
            } else if (GrantType.SAML20_BEARER.toString().equals(grantType) && Boolean.parseBoolean(isSAML2Enabled)) {
                authenticatedUser.setUserStoreDomain("FEDERATED");
                tokReqMsgCtx.setAuthorizedUser(authenticatedUser);
                Assertion assertion = (Assertion) tokReqMsgCtx.getProperty(ResourceConstants.SAML2_ASSERTION);
                userRoles = getRolesFromAssertion(assertion);
            } else if (JWTConstants.OAUTH_JWT_BEARER_GRANT_TYPE.equals(grantType) && !(Boolean
                    .parseBoolean(isRetrieveRolesFromUserStoreForScopeValidation))) {
                configureForJWTGrantOrExchangeGrant(tokReqMsgCtx, false);
                Map<ClaimMapping, String> userAttributes = authenticatedUser.getUserAttributes();
                if (tokReqMsgCtx.getProperty(ResourceConstants.ROLE_CLAIM) != null) {
                    userRoles = getRolesFromUserAttribute(userAttributes,
                            tokReqMsgCtx.getProperty(ResourceConstants.ROLE_CLAIM).toString());
                }
            } else {
                if (authenticatedUser.isFederatedUser() && StringUtils.equals(REFRESH_TOKEN_GRANT_TYPE, grantType)) {
                    try {
                        authenticatedUser.setUserName(OAuth2Util.getAppInformationByClientId(clientId).getAppOwner().
                                getUserName());
                    } catch (InvalidOAuthClientException | IdentityOAuth2Exception e) {
                        log.error("Error when retrieving the username " + e.getMessage(), e);
                    }
                }
                userRoles = getUserRoles(authenticatedUser, grantType);
            }
            authorizedScopes = getAuthorizedScopes(userRoles, requestedScopes, appScopes);
        }
        if (StringUtils.equals(CLIENT_CREDENTIALS_GRANT_TYPE, grantType)) {
            // If the grant type is client_credentials, add application scopes to the authorized scopes if requested
            try {
                Set<String> authorizedScopesSet = new HashSet<>(authorizedScopes);
                ServiceProvider sp = OAuth2Util.getServiceProvider(clientId);
                List<String> applicationScopes = getApplicationScopesFromSP(sp);
                authorizedScopesSet.remove(DEFAULT_SCOPE_NAME);
                boolean isMergeApplicationScopes = ServiceReferenceHolder.isMergeApplicationScopes();
                if (!isMergeApplicationScopes) {
                    authorizedScopesSet.clear();
                }
                for (String scope : applicationScopes) {
                    if (requestedScopes.contains(scope)) {
                        authorizedScopesSet.add(scope);
                    }
                }
                if (authorizedScopesSet.isEmpty() && authorizedScopes.contains(DEFAULT_SCOPE_NAME)) {
                    authorizedScopesSet.add(DEFAULT_SCOPE_NAME);
                }
                authorizedScopes = new ArrayList<>(authorizedScopesSet);
            } catch (IdentityOAuth2Exception e) {
                log.error("Error when retrieving the service provider " + e.getMessage(), e);
            }
        }
        scopes.addAll(authorizedScopes);
        return scopes;
    }

    /**
     * Retrieve application scopes from the service provider properties.
     *
     * @param sp ServiceProvider object
     * @return List of application scopes
     */
    private List<String> getApplicationScopesFromSP(ServiceProvider sp) {
        List<String> applicationScopes = new ArrayList<>();
        ServiceProviderProperty[] spProperties = sp.getSpProperties();
        if (spProperties != null) {
            for (ServiceProviderProperty property : spProperties) {
                if (property.getName().startsWith(APPLICATION_SCOPES_PREFIX)) {
                    applicationScopes.add(property.getValue());
                }
            }
        }
        return applicationScopes;
    }

    /**
     * This method is used to check whether a given scope is a product rest API scope or not.
     *
     * @param scope scope
     * @return true if it is a product rest API scope
     */
    private boolean checkForProductRestAPIScopes(String scope) {
        return scope.startsWith("apim:") || scope.startsWith("apim_analytics:") ||
                scope.startsWith("service_catalog:");
    }

    /**
     * This method is used to get roles list of the user.
     *
     * @param authenticatedUser Authenticated user
     * @return roles list
     */
    private String[] getUserRoles(AuthenticatedUser authenticatedUser, String grantType) {

        String[] userRoles = null;
        String tenantDomain;
        String username;
        if ((authenticatedUser.isFederatedUser() && StringUtils.equals(REFRESH_TOKEN_GRANT_TYPE, grantType))
                || !authenticatedUser.isFederatedUser()) {
            tenantDomain = authenticatedUser.getTenantDomain();
            username = authenticatedUser.getUserName();
        } else {
            tenantDomain = MultitenantUtils.getTenantDomain(authenticatedUser.getAuthenticatedSubjectIdentifier());
            username = MultitenantUtils.getTenantAwareUsername(
                    authenticatedUser.getAuthenticatedSubjectIdentifier());
        }
        String userStoreDomain = authenticatedUser.getUserStoreDomain();
        RealmService realmService = getRealmService();
        try {
            int tenantId = realmService.getTenantManager().getTenantId(tenantDomain);
            // If tenant Id is not set in the tokenReqContext, deriving it from username.
            if (tenantId == 0 || tenantId == -1) {
                tenantId = getTenantIdOfUser(username);
            }
            UserStoreManager userStoreManager = realmService.getTenantUserRealm(tenantId).getUserStoreManager();
            String endUsernameWithDomain = addDomainToName(username, userStoreDomain);
            userRoles = userStoreManager.getRoleListOfUser(endUsernameWithDomain);

        } catch (UserStoreException e) {
            //Log and return since we do not want to stop issuing the token in case of scope validation failures.
            log.error("Error when getting the tenant's UserStoreManager or when getting roles of user ", e);
        }
        return userRoles;
    }

    /**
     * This method is used to get authorized scopes for user from the requested scopes based on roles.
     *
     * @param userRoles       Roles list of user
     * @param requestedScopes Requested scopes
     * @param appScopes       Scopes of the Application
     * @return authorized scopes list
     */
    private List<String> getAuthorizedScopes(String[] userRoles, List<String> requestedScopes,
                                             Map<String, String> appScopes) {

        List<String> defaultScope = new ArrayList<>();
        defaultScope.add(DEFAULT_SCOPE_NAME);

        if (userRoles == null || userRoles.length == 0) {
            userRoles = new String[0];
        }

        List<String> authorizedScopes = new ArrayList<>();
        String preservedCaseSensitiveValue = System.getProperty(PRESERVED_CASE_SENSITIVE_VARIABLE);
        boolean preservedCaseSensitive = JavaUtils.isTrueExplicitly(preservedCaseSensitiveValue);
        List<String> userRoleList;
        if (preservedCaseSensitive) {
            userRoleList = Arrays.asList(userRoles);
        } else {
            userRoleList = new ArrayList<>();
            for (String aRole : userRoles) {
                userRoleList.add(aRole.toLowerCase(Locale.ENGLISH));
            }
        }

        //Iterate the requested scopes list.
        for (String scope : requestedScopes) {
            boolean isRestrictUnassignedScopes = ServiceReferenceHolder.isRestrictUnassignedScopes();
            //Get the set of roles associated with the requested scope.
            if ((isRestrictUnassignedScopes && oAuthServerConfiguration.getAllowedScopes().contains(scope)) ||
                    appScopes.containsKey(scope) || !isRestrictUnassignedScopes) {
                addAuthorizedRoles(appScopes, scope, preservedCaseSensitive, userRoleList, authorizedScopes);
            }
        }
        return (!authorizedScopes.isEmpty()) ? authorizedScopes : defaultScope;
    }

    private List<String> addAuthorizedRoles(Map<String, String> appScopes, String scope, boolean preservedCaseSensitive,
                                            List<String> userRoleList, List<String> authorizedScopes) {
        String roles = appScopes.get(scope);
        //If the scope has been defined in the context of the App and if roles have been defined for the scope
        if (roles != null && roles.length() != 0) {
            List<String> roleList = new ArrayList<>();
            for (String aRole : roles.split(",")) {
                if (preservedCaseSensitive) {
                    roleList.add(aRole.trim());
                } else {
                    roleList.add(aRole.trim().toLowerCase(Locale.ENGLISH));
                }
            }
            //Check if user has at least one of the roles associated with the scope
            roleList.retainAll(userRoleList);
            if (!roleList.isEmpty()) {
                authorizedScopes.add(scope);
            }
        } else {
            authorizedScopes.add(scope);
        }
        return authorizedScopes;
    }

    /**
     * Extract the roles from the user attributes.
     *
     * @param userAttributes retrieved from the token
     * @return roles
     */
    private String[] getRolesFromUserAttribute(Map<ClaimMapping, String> userAttributes, String roleClaim) {

        for (Iterator<Map.Entry<ClaimMapping, String>> iterator = userAttributes.entrySet().iterator(); iterator
                .hasNext(); ) {
            Map.Entry<ClaimMapping, String> entry = iterator.next();
            if (null != entry.getKey().getLocalClaim()) {
                if (roleClaim.equals(entry.getKey().getLocalClaim().getClaimUri()) && StringUtils
                        .isNotBlank(entry.getValue())) {
                    return entry.getValue().replace("\\/", "/").
                            replace("[", "").replace("]", "").
                            replace("\"", "").split(FrameworkUtils.getMultiAttributeSeparator());
                }
            }
        }
        return new String[0];
    }


    /**
     * Add domain to name
     *
     * @param username   Username
     * @param domainName domain name
     * @return String
     */
    protected String addDomainToName(String username, String domainName) {

        return UserCoreUtil.addDomainToName(username, domainName);
    }

    /**
     * Get roles from assertion
     *
     * @param assertion Assertion
     * @return String[]
     */
    protected String[] getRolesFromAssertion(Assertion assertion) {

        return TokenMgtUtil.getRolesFromAssertion(assertion);
    }

    protected void configureForJWTGrantOrExchangeGrant(OAuthTokenReqMessageContext tokReqMsgCtx,
                                                       boolean isExchangeGrant) {

        SignedJWT signedJWT = null;
        JWTClaimsSet claimsSet = null;
        String[] roles = null;
        try {
            if (isExchangeGrant) {
                signedJWT = getSignedJWTFromSubjectToken(tokReqMsgCtx);
            } else {
                signedJWT = getSignedJWT(tokReqMsgCtx);
            }
        } catch (IdentityOAuth2Exception e) {
            log.error("Couldn't retrieve signed JWT", e);
        }
        if (signedJWT != null) {
            claimsSet = getClaimSet(signedJWT);
        }
        String jwtIssuer = claimsSet != null ? claimsSet.getIssuer() : null;
        String tenantDomain = tokReqMsgCtx.getOauth2AccessTokenReqDTO().getTenantDomain();

        try {

            identityProvider = IdentityProviderManager.getInstance().getIdPByMetadataProperty(
                    IdentityApplicationConstants.IDP_ISSUER_NAME, jwtIssuer, tenantDomain, false);

            if (identityProvider == null) {
                if (log.isDebugEnabled()) {
                    log.debug("IDP not found when retrieving for IDP using property: " +
                            IdentityApplicationConstants.IDP_ISSUER_NAME + " with value: " + jwtIssuer +
                            ". Attempting to retrieve IDP using IDP Name as issuer.");
                }
                identityProvider = IdentityProviderManager.getInstance().getIdPByName(jwtIssuer, tenantDomain);
            }

            if (identityProvider != null) {
                if (StringUtils.equalsIgnoreCase(identityProvider.getIdentityProviderName(), "default")) {
                    identityProvider = this.getResidentIDPForIssuer(tenantDomain, jwtIssuer);
                    if (identityProvider == null) {
                        log.error("No Registered IDP found for the JWT with issuer name : " + jwtIssuer);
                    }
                }
            } else {
                log.error("No Registered IDP found for the JWT with issuer name : " + jwtIssuer);
            }
        } catch (IdentityProviderManagementException | IdentityOAuth2Exception e) {
            log.error("Couldn't initiate identity provider instance", e);
        }

        try {
            roles = claimsSet != null ?
                    claimsSet.getStringArrayClaim(identityProvider.getClaimConfig().getRoleClaimURI()) :
                    null;
        } catch (ParseException e) {
            log.error("Couldn't retrieve roles:", e);
        }

        List<String> updatedRoles = new ArrayList<>();
        if (roles != null) {
            for (String role : roles) {
                String updatedRoleClaimValue = getUpdatedRoleClaimValue(identityProvider, role);
                if (updatedRoleClaimValue != null) {
                    updatedRoles.add(updatedRoleClaimValue);
                } else {
                    updatedRoles.add(role);
                }
            }
        }
        AuthenticatedUser user = tokReqMsgCtx.getAuthorizedUser();
        Map<ClaimMapping, String> userAttributes = user.getUserAttributes();
        String roleClaim = identityProvider.getClaimConfig().getRoleClaimURI();
        if (roleClaim != null) {
            userAttributes
                    .put(ClaimMapping.build(roleClaim, roleClaim, null, false),
                            updatedRoles.toString().replace(" ", ""));
            tokReqMsgCtx.addProperty(ResourceConstants.ROLE_CLAIM, roleClaim);
        }
        user.setUserAttributes(userAttributes);
        tokReqMsgCtx.setAuthorizedUser(user);
    }

    /**
     * Method to parse the assertion and retrieve the signed JWT
     *
     * @param tokReqMsgCtx request
     * @return SignedJWT object
     * @throws IdentityOAuth2Exception exception thrown due to a parsing error
     */
    private SignedJWT getSignedJWT(OAuthTokenReqMessageContext tokReqMsgCtx) throws IdentityOAuth2Exception {

        RequestParameter[] params = tokReqMsgCtx.getOauth2AccessTokenReqDTO().getRequestParameters();
        String assertion = null;
        SignedJWT signedJWT;
        for (RequestParameter param : params) {
            if (param.getKey().equals(JWTConstants.OAUTH_JWT_ASSERTION)) {
                assertion = param.getValue()[0];
                break;
            }
        }
        if (StringUtils.isEmpty(assertion)) {
            String errorMessage = "Error while retrieving assertion";
            throw new IdentityOAuth2Exception(errorMessage);
        }

        try {
            signedJWT = SignedJWT.parse(assertion);
            if (log.isDebugEnabled()) {
                log.debug(signedJWT);
            }
        } catch (ParseException e) {
            String errorMessage = "Error while parsing the JWT.";
            throw new IdentityOAuth2Exception(errorMessage, e);
        }
        return signedJWT;
    }

    /**
     * Method to parse the subject token and retrieve the signed JWT
     *
     * @param tokReqMsgCtx request
     * @return SignedJWT object
     * @throws IdentityOAuth2Exception exception thrown due to a parsing error
     */
    private SignedJWT getSignedJWTFromSubjectToken(OAuthTokenReqMessageContext tokReqMsgCtx)
            throws IdentityOAuth2Exception {

        RequestParameter[] params = tokReqMsgCtx.getOauth2AccessTokenReqDTO().getRequestParameters();
        String subjectToken = null;
        SignedJWT signedJWT;
        for (RequestParameter param : params) {
            if (param.getKey().equals(ResourceConstants.SUBJECT_TOKEN)) {
                subjectToken = param.getValue()[0];
                break;
            }
        }
        if (StringUtils.isEmpty(subjectToken)) {
            String errorMessage = "Error while retrieving subjectToken";
            throw new IdentityOAuth2Exception(errorMessage);
        }

        try {
            signedJWT = SignedJWT.parse(subjectToken);
            if (log.isDebugEnabled()) {
                log.debug(signedJWT);
            }
        } catch (ParseException e) {
            String errorMessage = "Error while parsing the JWT.";
            throw new IdentityOAuth2Exception(errorMessage, e);
        }
        return signedJWT;
    }

    /**
     * Method to retrieve claims from the JWT
     *
     * @param signedJWT JWT token
     * @return JWTClaimsSet Object
     */
    private JWTClaimsSet getClaimSet(SignedJWT signedJWT) {

        JWTClaimsSet claimsSet = null;
        try {
            claimsSet = signedJWT.getJWTClaimsSet();
        } catch (ParseException e) {
            log.error("Error when trying to retrieve claimsSet from the JWT:", e);
        }
        return claimsSet;
    }

    private IdentityProvider getResidentIDPForIssuer(String tenantDomain, String jwtIssuer)
            throws IdentityOAuth2Exception {

        String issuer = "";
        IdentityProvider residentIdentityProvider;
        try {
            residentIdentityProvider = IdentityProviderManager.getInstance().getResidentIdP(tenantDomain);
        } catch (IdentityProviderManagementException var7) {
            String errorMsg = String
                    .format("Error while getting Resident Identity Provider of '%s' tenant.", tenantDomain);
            throw new IdentityOAuth2Exception(errorMsg, var7);
        }

        FederatedAuthenticatorConfig[] fedAuthnConfigs = residentIdentityProvider.getFederatedAuthenticatorConfigs();
        FederatedAuthenticatorConfig oauthAuthenticatorConfig = IdentityApplicationManagementUtil.
                getFederatedAuthenticator(fedAuthnConfigs, "openidconnect");
        if (oauthAuthenticatorConfig != null) {
            issuer = IdentityApplicationManagementUtil.getProperty(oauthAuthenticatorConfig.
                    getProperties(), "IdPEntityId").getValue();
        }

        return jwtIssuer.equals(issuer) ? residentIdentityProvider : null;
    }

    /**
     * Check the retireved roles against the role mappings in the IDP and return the updated roles
     *
     * @param identityProvider      used to retrieve the role mappings
     * @param currentRoleClaimValue current roles received through the token
     * @return updated roles
     */
    private String getUpdatedRoleClaimValue(IdentityProvider identityProvider, String currentRoleClaimValue) {

        if (StringUtils.equalsIgnoreCase(IdentityApplicationConstants.RESIDENT_IDP_RESERVED_NAME,
                identityProvider.getIdentityProviderName())) {
            return currentRoleClaimValue;
        }
        currentRoleClaimValue = currentRoleClaimValue.replace("\\/", "/")
                .replace("[", "").replace("]", "")
                .replace("\"", "");

        PermissionsAndRoleConfig permissionAndRoleConfig = identityProvider.getPermissionAndRoleConfig();
        if (permissionAndRoleConfig != null && org.apache.commons.lang3.ArrayUtils.isNotEmpty(
                permissionAndRoleConfig.getRoleMappings())) {
            String[] receivedRoles = currentRoleClaimValue.split(FrameworkUtils.getMultiAttributeSeparator());
            List<String> updatedRoleClaimValues = new ArrayList<>();
            String updatedLocalRole;
            loop:
            for (String receivedRole : receivedRoles) {
                for (RoleMapping roleMapping : permissionAndRoleConfig.getRoleMappings()) {
                    if (roleMapping.getRemoteRole().equals(receivedRole)) {
                        updatedLocalRole = StringUtils.isEmpty(roleMapping.getLocalRole().getUserStoreId())
                                ? roleMapping.getLocalRole().getLocalRoleName()
                                : roleMapping.getLocalRole().getUserStoreId() + UserCoreConstants.DOMAIN_SEPARATOR
                                + roleMapping.getLocalRole().getLocalRoleName();
                        updatedRoleClaimValues.add(updatedLocalRole);
                        continue loop;
                    }
                }
                if (!OAuthServerConfiguration.getInstance().isReturnOnlyMappedLocalRoles()) {
                    updatedRoleClaimValues.add(receivedRole);
                }
            }
            if (!updatedRoleClaimValues.isEmpty()) {
                return StringUtils.join(updatedRoleClaimValues, FrameworkUtils.getMultiAttributeSeparator());
            }
            return null;
        }
        if (!OAuthServerConfiguration.getInstance().isReturnOnlyMappedLocalRoles()) {
            return currentRoleClaimValue;
        }
        return null;
    }
}
