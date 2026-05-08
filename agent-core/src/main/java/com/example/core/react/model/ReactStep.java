package com.example.core.react.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactStep {

    @Builder.Default
    private int round = 0;

    private String thought;

    private String briefThought;

    private String action;

    private Object actionInput;

    private String observation;

    private String decision;

    @Builder.Default
    private boolean finished = false;
}
