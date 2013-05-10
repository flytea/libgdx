
package com.badlogic.gdx.maps.tiled;

import java.io.IOException;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.TextureLoader;
import com.badlogic.gdx.assets.loaders.TextureLoader.TextureParameter;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.ImageResolver;
import com.badlogic.gdx.maps.ImageResolver.AssetManagerImageResolver;
import com.badlogic.gdx.maps.ImageResolver.DirectImageResolver;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.XmlReader.Element;

public class TmxMapLoader extends TiledMapBaseLoader<TiledMap, TmxMapLoader.Parameters> {

	public static class Parameters extends AssetLoaderParameters<TiledMap> {
		/** Whether to load the map for a y-up coordinate system */
		public boolean yUp = true;
		/** generate mipmaps? **/
		public boolean generateMipMaps = false;
		/** The TextureFilter to use for minification **/
		public TextureFilter textureMinFilter = TextureFilter.Nearest;
		/** The TextureFilter to use for magnification **/
		public TextureFilter textureMagFilter = TextureFilter.Nearest;
	}

	private final ObjectMap<String, Texture> textures = new ObjectMap<String, Texture>();;
	private boolean yUp;
	private AssetManager assetManager;
	private ImageResolver resolver;

	public TmxMapLoader () {
		super(new InternalFileHandleResolver());
	}

	public TmxMapLoader (FileHandleResolver resolver) {
		super(resolver);
	}

	public TiledMap load (String fileName) {
		return load(fileName, new Parameters());
	}

	@Override
	public boolean isYUp () {
		return yUp;
	}

	@Override
	public TiledMap createTiledMap () {
		return new TiledMap();
	}

	@Override
	public void parseParameters (Parameters parameters, AssetManager assetManager) {
		this.assetManager = assetManager;
		if (parameters != null) {
			yUp = parameters.yUp;
		} else {
			yUp = true;
		}
	}

	@Override
	public ObjectMap<String, Texture> requestResources (FileHandle tmxFile, Element root, Parameters parameters) {
		try {
			for (FileHandle textureFile : loadTilesets(root, tmxFile)) {
				Texture texture = new Texture(textureFile, parameters.generateMipMaps);
				texture.setFilter(parameters.textureMinFilter, parameters.textureMagFilter);
				textures.put(textureFile.path(), texture);
			}
		} catch (IOException e) {
			throw new GdxRuntimeException("Couldn't load tilemap '" + tmxFile.path() + "'", e);
		}

		return textures;
	}

	@Override
	public Array<AssetDescriptor> requestDependancies (FileHandle tmxFile, Element root, Parameters parameters) {
		Array<AssetDescriptor> dependencies = new Array<AssetDescriptor>();

		boolean generateMipMaps = (parameters != null ? parameters.generateMipMaps : false);
		TextureLoader.TextureParameter texParams = new TextureParameter();
		texParams.genMipMaps = generateMipMaps;
		if (parameters != null) {
			texParams.minFilter = parameters.textureMinFilter;
			texParams.magFilter = parameters.textureMagFilter;
		}

		try {
			for (FileHandle image : loadTilesets(root, tmxFile)) {
				dependencies.add(new AssetDescriptor(image.path(), Texture.class, texParams));
			}
		} catch (IOException e) {
			throw new GdxRuntimeException("Couldn't load tilemap '" + tmxFile.path() + "'", e);
		}

		return dependencies;
	}

	@Override
	public void populateWithTiles (TiledMapTileSet tileset, TiledMap map, FileHandle tmxFile, FileHandle tilesetImage) {
		int tilewidth = tileset.getProperties().get("tilewidth", Integer.class);
		int tileheight = tileset.getProperties().get("tileheight", Integer.class);
		int spacing = tileset.getProperties().get("spacing", Integer.class);
		int margin = tileset.getProperties().get("margin", Integer.class);
		int firstgid = tileset.getProperties().get("firstgid", Integer.class);

		if (assetManager == null) {
			// sync load
			resolver = new DirectImageResolver(textures);
		} else {
			// async load
			resolver = new AssetManagerImageResolver(assetManager);
		}

		TextureRegion texture = resolver.getImage(tilesetImage.path());
		int stopWidth = texture.getRegionWidth() - tilewidth;
		int stopHeight = texture.getRegionHeight() - tileheight;

		int id = firstgid;

		for (int y = margin; y <= stopHeight; y += tileheight + spacing) {
			for (int x = margin; x <= stopWidth; x += tilewidth + spacing) {
				TextureRegion tileRegion = new TextureRegion(texture, x, y, tilewidth, tileheight);
				if (!isYUp()) {
					tileRegion.flip(false, true);
				}
				TiledMapTile tile = new StaticTiledMapTile(tileRegion);
				tile.setId(id);
				tileset.putTile(id++, tile);
			}
		}
	}

	/** Loads the tilesets
	 * @param root the root XML element
	 * @return a list of filenames for images containing tiles
	 * @throws IOException */
	private Array<FileHandle> loadTilesets (Element root, FileHandle tmxFile) throws IOException {
		Array<FileHandle> images = new Array<FileHandle>();
		for (Element tileset : root.getChildrenByName("tileset")) {
			String source = tileset.getAttribute("source", null);
			FileHandle image = null;
			if (source != null) {
				FileHandle tsx = getRelativeFileHandle(tmxFile, source);
				tileset = xml.parse(tsx);
				String imageSource = tileset.getChildByName("image").getAttribute("source");
				image = getRelativeFileHandle(tsx, imageSource);
			} else {
				String imageSource = tileset.getChildByName("image").getAttribute("source");
				image = getRelativeFileHandle(tmxFile, imageSource);
			}
			images.add(image);
		}
		return images;
	}
}
