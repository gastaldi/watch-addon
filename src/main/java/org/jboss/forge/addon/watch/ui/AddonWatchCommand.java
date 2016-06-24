/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.jboss.forge.addon.watch.ui;

import java.io.File;

import org.jboss.forge.addon.resource.FileResource;
import org.jboss.forge.addon.resource.ResourceFactory;
import org.jboss.forge.addon.ui.command.UICommand;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.output.UIOutput;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.addon.ui.util.Categories;
import org.jboss.forge.addon.ui.util.Metadata;
import org.jboss.forge.furnace.Furnace;
import org.jboss.forge.furnace.addons.Addon;
import org.jboss.forge.furnace.addons.AddonId;
import org.jboss.forge.furnace.addons.AddonRegistry;
import org.jboss.forge.furnace.manager.AddonManager;
import org.jboss.forge.furnace.repositories.MutableAddonRepository;
import org.jboss.forge.furnace.util.OperatingSystemUtils;
import org.jboss.forge.furnace.versions.Versions;

/**
 *
 * @author <a href="mailto:ggastald@redhat.com">George Gastaldi</a>
 */
public class AddonWatchCommand implements UICommand
{
   private AddonRegistry addonRegistry;
   private AddonManager addonManager;
   private ResourceFactory resourceFactory;

   @Override
   public void initializeUI(UIBuilder builder) throws Exception
   {
      addonRegistry = Furnace.instance(getClass().getClassLoader()).getAddonRegistry();
      addonManager = addonRegistry.getServices(AddonManager.class).get();
      resourceFactory = addonRegistry.getServices(ResourceFactory.class).get();
   }

   @Override
   public UICommandMetadata getMetadata(UIContext context)
   {
      return Metadata.forCommand(getClass()).name("Addon: Watch Start")
               .description("Start Watching when addons marked as SNAPSHOT are modified and reinstall it")
               .category(Categories.create("Addon"));
   }

   @SuppressWarnings("unchecked")
   @Override
   public Result execute(final UIExecutionContext executionContext) throws Exception
   {
      final UIOutput output = executionContext.getUIContext().getProvider().getOutput();
      addonRegistry
               .getAddons(addon -> Versions.isSnapshot(addon.getId().getVersion())
                        && addon.getRepository() instanceof MutableAddonRepository)
               .stream()
               .map(Addon::getId)
               .forEach(addonId -> {
                  // Find local repository path for each addon
                  File installationPath = getInstallationPathFor(addonId);
                  FileResource<?> resource = resourceFactory.create(FileResource.class, installationPath);
                  resource.monitor().addResourceListener(e -> {
                     // Run addonManager.remove and addonManager.install
                     addonManager.remove(addonId).perform();
                     addonManager.install(addonId).perform();
                  });
                  output.info(output.out(), "Monitoring changes on " + addonId);
               });
      return Results.success();
   }

   static File getInstallationPathFor(AddonId addonId)
   {
      String name = addonId.getName();
      StringBuilder sb = new StringBuilder(OperatingSystemUtils.getUserHomePath()).append("/.m2/repository/");
      sb.append(name.replace('.', '/').replace(':', '/'));
      sb.append("/").append(addonId.getVersion());
      sb.append("/").append(name.substring(name.lastIndexOf(":") + 1)).append("-").append(addonId.getVersion())
               .append(".jar");
      return new File(sb.toString());
   }

}
