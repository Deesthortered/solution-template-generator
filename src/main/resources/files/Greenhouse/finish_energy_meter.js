var newMsg = {
    'ts' : parseInt(metadata.ts),
    'values' : {
        'energyConsumptionLight' : parseFloat(metadata.values_energyConsumptionLight),
        'energyConsumptionHeating' : parseFloat(metadata.values_energyConsumptionHeating),
        'energyConsumptionCooling' : parseFloat(metadata.values_energyConsumptionCooling),
        'energyConsumptionAirControl' : parseFloat(metadata.values_energyConsumptionAirControl),
        'energyConsumptionIrrigation' : parseFloat(metadata.values_energyConsumptionIrrigation)
    }
};
return {msg: newMsg, metadata: metadata, msgType: msgType};