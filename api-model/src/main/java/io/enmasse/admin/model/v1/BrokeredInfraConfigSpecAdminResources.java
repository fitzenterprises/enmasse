/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.admin.model.v1;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.fabric8.kubernetes.api.model.Doneable;
import io.sundr.builder.annotations.Buildable;
import io.sundr.builder.annotations.Inline;

@Buildable(
        editableEnabled = false,
        generateBuilderPackage = false,
        builderPackage = "io.fabric8.kubernetes.api.builder",
        inline = @Inline(type = Doneable.class, prefix = "Doneable", value = "done")
)
@JsonPropertyOrder({"memory"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BrokeredInfraConfigSpecAdminResources extends AbstractWithAdditionalProperties {
    private String memory;

    public BrokeredInfraConfigSpecAdminResources() {
    }

    public BrokeredInfraConfigSpecAdminResources(final String memory) {
        setMemory(memory);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BrokeredInfraConfigSpecAdminResources that = (BrokeredInfraConfigSpecAdminResources) o;
        return Objects.equals(memory, that.memory);
    }

    @Override
    public String toString() {
        return "BrokeredInfraConfigSpecAdminResources{" +
                "memory='" + memory + '\'' +
                '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(memory);
    }

    public void setMemory(String memory) {
        this.memory = memory;
    }

    public String getMemory() {
        return memory;
    }

}
