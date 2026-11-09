"""VRChat profile artwork: safe, bounded, asynchronous loading helpers.

Image URLs originate in VRChat profile/world/group API responses, but only
approved VRChat image/CDN hosts are fetched. No cookies, VRChat credentials,
Relay tokens, disk cache, HTML rendering, arbitrary redirects or tracking.
Raster decoding is delegated to Pillow, with compressed and pixel limits.
"""
from __future__ import annotations

from collections import OrderedDict
from io import BytesIO
from urllib.parse import urlsplit
from urllib.request import Request, build_opener, HTTPRedirectHandler
import threading
import time

MAX_BYTES = 5 * 1024 * 1024
MAX_PIXELS = 18_000_000
CACHE_COUNT = 32
CACHE_TTL = 12 * 60
VALID_HOSTS = (
    "vrchat.cloud", "vrchat.com", "vrcimg.com",
)


def safe_image_url(value):
    if not isinstance(value,str) or not 8 <= len(value) <= 2048:
        return None
    try:
        parsed=urlsplit(value)
        host=parsed.hostname or ""
        if (parsed.scheme != "https" or parsed.username or parsed.password or
            parsed.port not in (None,443) or parsed.fragment or not host):
            return None
        host=host.lower().rstrip(".")
        if not any(host == suffix or host.endswith("."+suffix)
                   for suffix in VALID_HOSTS):
            return None
        return value
    except (ValueError,TypeError):
        return None


def image_sources(record, kind="user"):
    """Return primary image and banner, favoring actual user profile assets."""
    if not isinstance(record,dict):
        return None,None
    if kind=="user":
        primary=("profilePicOverride","profilePicUrl","userIcon",
                 "currentAvatarThumbnailImageUrl","currentAvatarImageUrl",
                 "thumbnailImageUrl","imageUrl")
        banners=("profilePicOverride","profilePicUrl","profilePic",
                 "currentAvatarImageUrl","imageUrl")
    elif kind=="world":
        primary=("thumbnailImageUrl","imageUrl","featuredImageUrl")
        banners=("imageUrl","featuredImageUrl","thumbnailImageUrl")
    else:
        primary=("iconUrl","thumbnailUrl","thumbnailImageUrl","imageUrl")
        banners=("bannerUrl","bannerImageUrl","imageUrl","iconUrl")
    def get(keys):
        for key in keys:
            val=record.get(key)
            if safe_image_url(val):
                return val
        return None
    return get(primary),get(banners)


class _SameHostRedirects(HTTPRedirectHandler):
    """Reject cross-host redirects before they can disclose request traffic."""
    def redirect_request(self,req,fp,code,msg,headers,newurl):
        if not safe_image_url(newurl):
            raise ValueError("Untrusted VRChat image redirect")
        return super().redirect_request(req,fp,code,msg,headers,newurl)


class ProfileImageCache:
    def __init__(self,clock=time.monotonic):
        self.clock=clock
        self._lock=threading.RLock()
        self._items=OrderedDict()
        self._opener=build_opener(_SameHostRedirects())

    def fetch(self,url):
        allowed=safe_image_url(url)
        if not allowed:
            raise ValueError("Profile image URL is not an approved VRChat HTTPS host")
        with self._lock:
            item=self._items.get(allowed)
            if item and self.clock()-item[0] <= CACHE_TTL:
                self._items.move_to_end(allowed)
                return item[1]
        request=Request(allowed,headers={
            "User-Agent":"TGC-CoCompanion-OwnerPreview/Profiles",
            "Accept":"image/png,image/jpeg,image/webp,image/gif",
        })
        with self._opener.open(request,timeout=8) as response:
            if not safe_image_url(response.geturl()):
                raise ValueError("Untrusted redirected image host")
            payload=response.read(MAX_BYTES+1)
            if len(payload)>MAX_BYTES:
                raise ValueError("Profile picture exceeds safe image download limit")
            mime=response.headers.get_content_type()
            if mime not in ("image/png","image/jpeg","image/webp","image/gif",
                            "application/octet-stream"):
                raise ValueError("Response was not a supported image")
        # Parse/verify the image in the worker before any Tk calls.
        from PIL import Image, UnidentifiedImageError
        with Image.open(BytesIO(payload)) as probe:
            if probe.width*probe.height>MAX_PIXELS:
                raise ValueError("Profile image exceeds safe pixel limit")
            if probe.format not in ("PNG","JPEG","WEBP","GIF"):
                raise ValueError("Unsupported image format")
            probe.verify()
        with self._lock:
            self._items[allowed]=(self.clock(),payload)
            self._items.move_to_end(allowed)
            while len(self._items)>CACHE_COUNT:
                self._items.popitem(last=False)
        return payload


def render_image(data,size,mode="cover",circle=False):
    """Return a PIL.Image ready to wrap in ImageTk.PhotoImage on the GUI thread."""
    from PIL import Image, ImageDraw, ImageOps
    with Image.open(BytesIO(data)) as source:
        img=source.convert("RGBA")
    w,h=size
    if mode=="contain":
        img.thumbnail((w,h),Image.Resampling.LANCZOS)
        frame=Image.new("RGBA",(w,h),(18,33,54,255))
        frame.alpha_composite(img,((w-img.width)//2,(h-img.height)//2))
    else:
        frame=ImageOps.fit(img,(w,h),method=Image.Resampling.LANCZOS,
                           centering=(.5,.35))
    if circle:
        mask=Image.new("L",(w,h),0)
        ImageDraw.Draw(mask).ellipse((1,1,w-2,h-2),fill=255)
        frame.putalpha(mask)
    return frame
