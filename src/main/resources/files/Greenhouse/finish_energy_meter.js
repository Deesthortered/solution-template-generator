var newMsg = {
    'ts' : parseInt(metadata.ts),
    'values' : {
        'energyConsumptionLight' : parseInt(metadata.values_energyConsumptionLight),
        'energyConsumptionHeating' : parseInt(metadata.values_energyConsumptionHeating),
        'energyConsumptionCooling' : parseInt(metadata.values_energyConsumptionCooling),
        'energyConsumptionAirControl' : parseInt(metadata.values_energyConsumptionAirControl),
        'energyConsumptionIrrigation' : parseInt(metadata.values_energyConsumptionIrrigation)
    }
};
return {msg: newMsg, metadata: metadata, msgType: msgType};