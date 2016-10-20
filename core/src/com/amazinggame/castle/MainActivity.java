package com.amazinggame.castle;

import com.amazinggame.castle.model.Position;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.math.Vector3;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MainActivity extends ApplicationAdapter implements InputProcessor {

    // length along romb side normal vector to bounding line, that is parallel to romb side.
    private static final float MAP_BOUND_DELTA = 50;

    private OrthographicCamera camera;
    private TmxMapRenderer tiledMapRenderer;

    private Position screenStartPos;
    private boolean moving = false;
    private Vector3 cameraStartPos;
    private Position prevDragPos = null;
    private Position dragPos = null;

    private long endMSec = 0;
    private long startMSec = 0;

    private Slider slider = null;

    private int mapRowCount = 0;
    private int mapColCount = 0;

    private float minCameraX = 0f;
    private float minCameraY = 0f;
    private float maxCameraX = 0f;
    private float maxCameraY = 0f;
    private float centerCameraX = 0f;
    private float centerCameraY = 0f;
    private float mapExtentsTanRatio = 0f; // map bounding height / map bounding width
    private float mapExtentsCosRatio = 0f; // map bounding width / sqrt(h*h + w*w)
    private float mapExtentsSinRatio = 0f; // map bounding height / sqrt(h*h + w*w)
    private float mapBoundDeltaY = 0f; // map bounding Y offset:

    private final Lock cameraLock = new ReentrantLock();
    private float tileWidth;
    private float tileHeight;
    private float halfTileWidth;
    private float halfTileHeight;
    private TiledMap tiledMap;
    private float zoom;

    @Override
    public void create () {
        tiledMap = new TmxMapLoader().load("100x100.tmx");
        tiledMapRenderer = new TmxMapRenderer(tiledMap);
        Gdx.input.setInputProcessor(this);

        mapRowCount = Integer.parseInt(String.valueOf(tiledMap.getProperties().get("height")));
        mapColCount = Integer.parseInt(String.valueOf(tiledMap.getProperties().get("width")));
        tileWidth = Integer.parseInt(String.valueOf(tiledMap.getProperties().get("tilewidth")));
        tileHeight = Integer.parseInt(String.valueOf(tiledMap.getProperties().get("tileheight")));
        halfTileWidth = tileWidth * 0.5f;
        halfTileHeight = tileHeight * 0.5f;

        zoom = 1;

        computeCameraExtents();

        //camera
        camera = new OrthographicCamera();
        camera.zoom = zoom;
        camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(minCameraX + (maxCameraX - minCameraX) / 2, minCameraY + (maxCameraY - minCameraY) / 2, 0);
        camera.update();
    }

    @Override
    public void render () {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        cameraLock.lock();
        try {
            camera.update();
            tiledMapRenderer.setView(camera);
            tiledMapRenderer.render();
        } finally {
            cameraLock.unlock();
        }
    }

    @Override
    public void resize(int width, int height) {
        cameraLock.lock();
        try {
            final Vector3 camPos = camera.position;
            camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            camera.position.set(camPos);
            computeCameraExtents();
            boundCamera();
        } finally {
            cameraLock.unlock();
        }
    }

    @Override
    public boolean keyDown(int keycode) {
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        return false;
    }

    @Override
    public boolean keyTyped(char character) {
        return false;
    }

    @Override
    public boolean mouseMoved(final int screenX, final int screenY) {
        return false;
    }

    @Override
    public boolean scrolled(final int amount) {
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        moving = false;

        if (slider != null) {
            slider.cancel();
        }

        dragPos = new Position(screenX, screenY);

        float dx = prevDragPos.getX() - dragPos.getX();
        float dy = dragPos.getY() - prevDragPos.getY();
        float len = (float) Math.sqrt(dx*dx + dy*dy);
        if (len == 0) return false;

        final Position direction =  new Position(dx / len, dy / len);
        final float velocity = Math.min(100, len);
        slider = new Slider(velocity * zoom, direction);

        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (slider != null) {
            slider.cancel();
        }

        cameraLock.lock();
        try {
            moving = true;

            prevDragPos = new Position(screenX, screenY);

            screenStartPos = new Position(screenX, screenY);
            cameraStartPos = new Vector3(camera.position);

            startMSec = System.currentTimeMillis();
            endMSec = startMSec;
        } finally {
            cameraLock.unlock();
        }

        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        if (!moving) return false;

        cameraLock.lock();
        try {
            dragPos = new Position(screenX, screenY);

            endMSec = System.currentTimeMillis();
            if (endMSec - startMSec < 10) return false;

            startMSec = endMSec;

            camera.position.set(
                    cameraStartPos.x - (screenX - screenStartPos.getX()) * zoom,
                    cameraStartPos.y + (screenY - screenStartPos.getY()) * zoom,
                    0);
            boundCamera();

            prevDragPos = dragPos;
        } finally {
            cameraLock.unlock();
        }

        return false;
    }

    private void computeCameraExtents() {
        float width = Gdx.graphics.getWidth() * zoom;
        float height = Gdx.graphics.getHeight() * zoom;
        final float halfWidth = width / 2;
        final float halfHelght = height / 2;
        float minBoundX = 0;
        float minBoundY = -(mapColCount-1) * halfTileHeight - height;
        float maxBoundX = (mapColCount + mapRowCount - 2) * halfTileWidth + tileWidth + 4;
        float maxBoundY = (mapRowCount-1) * halfTileHeight + tileHeight + 4 - height;

        minCameraX = halfWidth + minBoundX;
        minCameraY = halfHelght + minBoundY + height;
        maxCameraX = maxBoundX - halfWidth;
        maxCameraY = maxBoundY - halfHelght + height;

        centerCameraX = minCameraX + (maxCameraX - minCameraX) / 2;
        centerCameraY = minCameraY + (maxCameraY - minCameraY) / 2;

        final float boundDx = maxBoundX - minBoundX;
        final float boundDy = maxBoundY - minBoundY;
        mapExtentsTanRatio = boundDy / boundDx;
        final float hipotenusa = (float) Math.sqrt(boundDx * boundDx + boundDy * boundDy);
        mapExtentsCosRatio = boundDx / hipotenusa;
        mapExtentsSinRatio = boundDy / hipotenusa;
        mapBoundDeltaY = (float) Math.sqrt(MAP_BOUND_DELTA * MAP_BOUND_DELTA * (mapExtentsTanRatio * mapExtentsTanRatio + 1));
    }

    private void boundCamera() {
        float cx = camera.position.x;
        float cy = camera.position.y;

        if (cx < minCameraX) cx = minCameraX;
        if (cy < minCameraY) cy = minCameraY;
        if (cx > maxCameraX) cx = maxCameraX;
        if (cy > maxCameraY) cy = maxCameraY;

        final Position newCamPos;

        if (cx == centerCameraX || cy == centerCameraY) {
            newCamPos = new Position(cx, cy);
        } else {
            if (cx < centerCameraX) {
                if (cy >= centerCameraY) {
                    newCamPos = moveBackToBoundingLine(cx, cy, true, true);
                } else {
                    newCamPos = moveBackToBoundingLine(cx, cy, true, false);
                }
            } else {
                if (cy >= centerCameraY) {
                    newCamPos = moveBackToBoundingLine(cx, cy, false, true);
                } else {
                    newCamPos = moveBackToBoundingLine(cx, cy, false, false);
                }
            }
        }

        camera.position.x = newCamPos.getX();
        camera.position.y = newCamPos.getY();
    }

    // left==true, up==true   =>    upper-left corner
    // left==false, up==true  =>    upper-right corner
    // etc...
    private Position moveBackToBoundingLine(float cx, float cy, boolean left, boolean up) {
        final float dx = Math.abs(cx - (left ? minCameraX : maxCameraX));
        final float dy = Math.abs(cy - centerCameraY);
        final float maxDy = dx * mapExtentsTanRatio + mapBoundDeltaY; // maximum dY to be on or lower bounding line
        // outside bounding line -> move back along normal vector
        if (dy > maxDy) {
            final float dyOverhead = dy - maxDy; // Y-offset upper from bounding line
            final float backAlongNormalLength = dyOverhead * mapExtentsCosRatio; // length to go along normal vector back to bounding line
            final float backToLineDx = backAlongNormalLength * mapExtentsSinRatio;
            final float backToLineDy = backAlongNormalLength * mapExtentsCosRatio;
            // return camera to bounding line along normal vector
            return
                    new Position(
                            cx + backToLineDx * (left ? 1 : -1),
                            cy - backToLineDy * (up ? 1 : -1)
                    );
        } else {
            return new Position(cx, cy);
        }
    }

    private class Slider extends Thread {
        private boolean cancelled;
        private float velocity;
        private Position direction;

        Slider(float velocity, Position direction) {
            super();
            this.velocity = velocity;
            this.direction = direction;
            this.cancelled = false;
            start();
        }

        public void cancel() {
            cancelled = true;
            try {
                join();
            } catch (InterruptedException e) {
            }
        }

        @Override
        public void run() {
            while (!cancelled && velocity > 2) {
                cameraLock.lock();
                try {
                    camera.translate(velocity * direction.getX(), velocity * direction.getY(), 0);
                    boundCamera();
                } finally {
                    cameraLock.unlock();
                }
                try {
                    sleep(20);
                    velocity = velocity * 0.9f;
                } catch (InterruptedException e) {
                }
            }
        }
    }
}
