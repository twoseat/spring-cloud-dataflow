/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.dataflow.completion;

import static org.springframework.cloud.dataflow.completion.CompletionProposal.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.configurationmetadata.ConfigurationMetadataProperty;
import org.springframework.cloud.dataflow.core.ArtifactType;
import org.springframework.cloud.dataflow.core.ModuleDefinition;
import org.springframework.cloud.dataflow.core.StreamDefinition;
import org.springframework.cloud.dataflow.artifact.registry.ArtifactRegistration;
import org.springframework.cloud.dataflow.artifact.registry.ArtifactRegistry;
import org.springframework.cloud.stream.configuration.metadata.ModuleConfigurationMetadataResolver;
import org.springframework.cloud.stream.module.resolver.ModuleResolver;
import org.springframework.core.io.Resource;

/**
 * Adds missing module configuration properties at the end of a well formed stream definition.
 *
 * @author Eric Bottard
 */
class AddModuleOptionsExpansionStrategy implements ExpansionStrategy {

	private final ArtifactRegistry artifactRegistry;

	private final ModuleConfigurationMetadataResolver moduleConfigurationMetadataResolver;

	private final ModuleResolver moduleResolver;

	public AddModuleOptionsExpansionStrategy(ArtifactRegistry artifactRegistry,
			ModuleConfigurationMetadataResolver moduleConfigurationMetadataResolver,
			ModuleResolver moduleResolver) {
		this.artifactRegistry = artifactRegistry;
		this.moduleConfigurationMetadataResolver = moduleConfigurationMetadataResolver;
		this.moduleResolver = moduleResolver;
	}

	@Override
	public boolean addProposals(String text, StreamDefinition streamDefinition, int detailLevel,
			List<CompletionProposal> collector) {
		ModuleDefinition lastModule = streamDefinition.getDeploymentOrderIterator().next();

		String lastModuleName = lastModule.getName();
		ArtifactRegistration lastArtifactRegistration = null;
		for (ArtifactType moduleType : CompletionUtils.determinePotentialTypes(lastModule)) {
			lastArtifactRegistration = artifactRegistry.find(lastModuleName, moduleType);
			if (lastArtifactRegistration != null) {
				break;
			}
		}
		if (lastArtifactRegistration == null) {
			// Not a valid module name, do nothing
			return false;
		}
		Set<String> alreadyPresentOptions = new HashSet<>(lastModule.getParameters().keySet());

		Resource jarFile = moduleResolver.resolve(CompletionUtils.fromModuleCoordinates(lastArtifactRegistration.getCoordinates()));

		CompletionProposal.Factory proposals = expanding(text);

		for (ConfigurationMetadataProperty property : moduleConfigurationMetadataResolver.listProperties(jarFile)) {
			if (!alreadyPresentOptions.contains(property.getId())) {
				collector.add(proposals.withSeparateTokens("--" + property.getId() + "=", property.getShortDescription()));
			}
		}
		return false;

	}

}