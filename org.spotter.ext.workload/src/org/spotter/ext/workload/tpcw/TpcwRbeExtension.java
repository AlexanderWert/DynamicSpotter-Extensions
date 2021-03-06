package org.spotter.ext.workload.tpcw;

import java.util.HashSet;
import java.util.Set;

import org.lpe.common.config.ConfigParameterDescription;
import org.lpe.common.util.LpeSupportedTypes;
import org.spotter.core.workload.AbstractWorkloadExtension;
import org.spotter.core.workload.IWorkloadAdapter;

public class TpcwRbeExtension extends AbstractWorkloadExtension {
	private static final String EXTENSION_DESCRIPTION = "Used for TPCW Remote Browser Emulator!";

	public static final String PAR_EB_FACTORY = "EB factory";
	public static final String PAR_THINK_TIME = "think time factor";
	public static final String PAR_NUM_CUSTOMERS = "DB num customers";
	public static final String PAR_NUM_ITEMS = "DB num items";
	public static final String PAR_URL = "base URL";
	public static final String BROWSING_MIX = "Browsing Mix";
	public static final String SHOPPING_MIX = "Shopping Mix";
	public static final String ORDERING_MIX = "Ordering Mix";

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return "TPCW RBE Extension";
	}

	@Override
	public IWorkloadAdapter createExtensionArtifact() {
		return new TpcwRbeDriver(this);
	}

	private ConfigParameterDescription createEBFactoryParameter() {
		ConfigParameterDescription parameter = new ConfigParameterDescription(PAR_EB_FACTORY, LpeSupportedTypes.String);
		parameter.setMandatory(true);

		Set<String> options = new HashSet<>();
		options.add(BROWSING_MIX);
		options.add(SHOPPING_MIX);
		options.add(ORDERING_MIX);
		parameter.setOptions(options);
		parameter.setDefaultValue(BROWSING_MIX);
		parameter.setDescription("Mix Type");

		return parameter;
	}

	private ConfigParameterDescription createThinkTimeFactorParameter() {
		ConfigParameterDescription parameter = new ConfigParameterDescription(PAR_THINK_TIME, LpeSupportedTypes.Double);
		parameter.setMandatory(true);
		parameter.setDefaultValue(String.valueOf(1.0));

		parameter.setDescription("number of emulated browsers");

		return parameter;
	}

	private ConfigParameterDescription createNumDBCustomersParameter() {
		ConfigParameterDescription parameter = new ConfigParameterDescription(PAR_NUM_CUSTOMERS,
				LpeSupportedTypes.Integer);
		parameter.setMandatory(true);
		parameter.setDefaultValue(String.valueOf(100));

		parameter.setDescription("number of customers in the database");

		return parameter;
	}

	private ConfigParameterDescription createNumDBItemsParameter() {
		ConfigParameterDescription parameter = new ConfigParameterDescription(PAR_NUM_ITEMS, LpeSupportedTypes.Integer);
		parameter.setMandatory(true);
		parameter.setDefaultValue(String.valueOf(100));

		parameter.setDescription("number of items in the database");

		return parameter;
	}

	private ConfigParameterDescription createURLParameter() {
		ConfigParameterDescription parameter = new ConfigParameterDescription(PAR_URL, LpeSupportedTypes.String);
		parameter.setMandatory(true);
		parameter.setDefaultValue("http://");
		parameter.setDescription("Base URL of the TPC-W application");

		return parameter;
	}

	@Override
	protected void initializeConfigurationParameters() {
		addConfigParameter(createEBFactoryParameter());
		addConfigParameter(createThinkTimeFactorParameter());
		addConfigParameter(createNumDBCustomersParameter());
		addConfigParameter(createNumDBItemsParameter());
		addConfigParameter(createURLParameter());
	}

	@Override
	public boolean testConnection(String host, String port) {
		return true;
	}

	@Override
	public boolean isRemoteExtension() {
		return false;
	}
}
