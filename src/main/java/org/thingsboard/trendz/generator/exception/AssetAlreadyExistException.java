package org.thingsboard.trendz.generator.exception;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.thingsboard.server.common.data.asset.Asset;

@Getter
@EqualsAndHashCode(callSuper = true)
public class AssetAlreadyExistException extends SolutionTemplateGeneratorException {

    private final Asset asset;

    public AssetAlreadyExistException(Asset asset) {
        super("Asset is already exists: " + asset.getName());
        this.asset = asset;
    }
}
