package org.thingsboard.trendz.generator.exception;

import org.thingsboard.server.common.data.asset.Asset;

public class AssetAlreadyExistException extends SolutionTemplateGeneratorException {

    private final Asset asset;

    public AssetAlreadyExistException(Asset asset) {
        this.asset = asset;
    }
}
